package com.taehyung.mes.lot;

import com.taehyung.mes.common.actor.EnvironmentActorProvider;
import com.taehyung.mes.item.ItemMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 사전에 schema.sql을 적용한 전용 로컬 스키마만 사용한다. 운영 DB_* / application.yml은 읽지 않는다. */
@EnabledIfEnvironmentVariable(named="MES_RAW_LOT_MYSQL_TEST", matches="LOCAL_TEST_ONLY")
class RawMaterialLotMysqlIntegrationTests {
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private RawMaterialLotMapper mapper;
    private ItemMapper items;
    private RawMaterialLotService service;
    private String actor;
    private Long rawId;

    @BeforeEach void setUp() throws Exception {
        String database = env("RAW_LOT_TEST_DB_NAME");
        if (!database.matches("[a-z0-9_]+_raw_lot_test")) throw new IllegalArgumentException("Use a dedicated *_raw_lot_test schema.");
        int port = Integer.parseInt(System.getenv().getOrDefault("RAW_LOT_TEST_DB_PORT", "3306"));
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid local test port.");
        var source = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true", env("RAW_LOT_TEST_DB_USER"), env("RAW_LOT_TEST_DB_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(database);
        transactions = new DataSourceTransactionManager(source); transactions.setDefaultTimeout(10);
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
        var config = new Configuration(); config.setMapUnderscoreToCamelCase(true); factory.setConfiguration(config);
        var resolver = new PathMatchingResourcePatternResolver();
        factory.setMapperLocations(resolver.getResource("classpath:mappers/lot/RawMaterialLotMapper.xml"), resolver.getResource("classpath:mappers/item/ItemMapper.xml"));
        factory.afterPropertiesSet(); var sessions = new SqlSessionTemplate(factory.getObject());
        mapper = sessions.getMapper(RawMaterialLotMapper.class); items = sessions.getMapper(ItemMapper.class);
        actor = "RAW_IT_" + UUID.randomUUID(); rawId = item("-R1", "RAW", "EA"); service = transactional(mapper);
    }

    @AfterEach void cleanOwnFixtures() {
        if (actor == null) return;
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            for (String table : new String[]{"material_return", "material_consumption", "request_receipt", "raw_material_lot", "work_order",
                    "bom_component", "bom_version", "bom", "routing_revision", "routing", "production_plan", "item"}) {
                jdbc.update("DELETE FROM " + table + " WHERE created_by=?", actor);
            }
        });
    }

    @Test void persistsEqualInitialQuantitiesServerUtcTimeAndSearchableLot() {
        LocalDateTime before = jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class);
        Long id = service.create(form(rawId, actor, "10"));
        LocalDateTime after = jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class);
        RawMaterialLot lot = service.get(id);
        assertThat(lot.getRegisteredQty()).isEqualByComparingTo("10");
        assertThat(lot.getAvailableQty()).isEqualByComparingTo(lot.getRegisteredQty());
        assertThat(lot.getRegisteredAt()).isBetween(before, after);
        assertThat(lot.getCreatedAt()).isNotNull(); assertThat(lot.getCreatedBy()).isEqualTo(actor);
        assertThat(lot.getConsumedQty()).isEqualByComparingTo("0"); assertThat(lot.isQuantityConsistent()).isTrue();
        assertThat(service.list(actor, actor, "원자재")).extracting(RawMaterialLot::getRawLotId).containsExactly(id);
        assertThat(service.choices()).extracting(com.taehyung.mes.item.Item::getItemId).contains(rawId);
    }

    @Test void storesMgFractionsExactlyAndRejectsInvalidQuantitiesAndDuplicates() {
        for (String unit : new String[]{"M", "G"}) {
            Long id = item("-" + unit, "RAW", unit);
            assertThat(service.get(service.create(form(id, actor + unit, "0.000001"))).getRegisteredQty()).isEqualByComparingTo("0.000001");
        }
        for (String quantity : new String[]{"0", "-1", "1.5", "1.0000001", "1000000000000"}) {
            assertThatThrownBy(() -> service.create(form(rawId, actor, quantity))).isInstanceOf(RawMaterialLotRuleException.class);
        }
        service.create(form(rawId, actor, "10"));
        assertThatThrownBy(() -> service.create(form(rawId, actor, "10"))).isInstanceOf(RawMaterialLotRuleException.class);
        Long other = item("-R2", "RAW", "EA");
        assertThatThrownBy(() -> service.create(form(other, actor, "10"))).isInstanceOf(RawMaterialLotRuleException.class);
    }

    @Test void databaseUniqueProtectsSimultaneousRegistrationsAfterBothPrechecksPassed() throws Exception {
        Long other = item("-R2", "RAW", "EA"); var checked = new CountDownLatch(2);
        RawMaterialLotMapper racing = spy(mapper);
        doAnswer(call -> {
            boolean exists = mapper.existsByLotNo(call.getArgument(0)); checked.countDown(); await(checked); return exists;
        }).when(racing).existsByLotNo(actor);
        RawMaterialLotService writer = transactional(racing); var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> duplicateOutcome(writer, rawId));
            var second = pool.submit(() -> duplicateOutcome(writer, other));
            assertThat(first.get(15, TimeUnit.SECONDS) + second.get(15, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM raw_material_lot WHERE lot_no=?", Long.class, actor)).isEqualTo(1L);
        } finally { pool.shutdownNow(); if (!pool.awaitTermination(12, TimeUnit.SECONDS)) throw new IllegalStateException("Test threads did not stop."); }
    }

    @Test void actualCheckFailureAfterInsertRollsBackTheWholeLot() {
        RawMaterialLotMapper failing = spy(mapper);
        doAnswer(call -> {
            RawMaterialLot lot = call.getArgument(0); mapper.insert(lot);
            jdbc.update("UPDATE raw_material_lot SET available_qty=-1 WHERE raw_lot_id=?", lot.getRawLotId()); return 1;
        }).when(failing).insert(any());
        Throwable failure = catchThrowable(() -> transactional(failing).create(form(rawId, actor, "10")));
        assertThat(failure).isInstanceOf(DataAccessException.class);
        Throwable cause = failure;
        while (cause != null && !(cause instanceof java.sql.SQLException)) { cause = cause.getCause(); }
        assertThat(cause).isInstanceOf(java.sql.SQLException.class);
        assertThat(((java.sql.SQLException) cause).getErrorCode()).isEqualTo(3819);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM raw_material_lot WHERE created_by=?", Long.class, actor)).isZero();
    }

    @Test void registrationWaitsForDeactivationAndRejectsTheLatestInactiveItem() throws Exception {
        var pool = Executors.newFixedThreadPool(2); var locked = new CountDownLatch(1); var release = new CountDownLatch(1); var started = new CountDownLatch(1);
        try {
            var holder = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                items.findByIdForUpdate(rawId); locked.countDown(); await(release);
                jdbc.update("UPDATE item SET is_active=0 WHERE item_id=?", rawId);
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var writer = pool.submit(() -> { started.countDown(); return service.create(form(rawId, actor, "10")); });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> writer.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); holder.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> writer.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(RawMaterialLotRuleException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM raw_material_lot WHERE created_by=?", Long.class, actor)).isZero();
        } finally { release.countDown(); pool.shutdownNow(); if (!pool.awaitTermination(12, TimeUnit.SECONDS)) throw new IllegalStateException("Test threads did not stop."); }
    }

    @Test void separatelyAggregatesMultipleConsumptionsAndReturnsWithoutRowMultiplication() {
        Long lotId = service.create(form(rawId, actor, "100"));
        Long product = item("-P", "FINISHED", "EA");
        jdbc.update("INSERT INTO production_plan(product_item_id,planned_qty,planned_date,created_by) VALUES (?,100,'2026-10-27',?)", product, actor);
        Long plan = ownId("production_plan", "production_plan_id");
        jdbc.update("INSERT INTO bom(product_item_id,bom_code,created_by) VALUES (?,?,?)", product, actor, actor); Long bom = ownId("bom", "bom_id");
        jdbc.update("INSERT INTO bom_version(bom_id,version_no,base_qty,product_unit_code,first_used_at,created_by) VALUES (?,1,1,'EA',UTC_TIMESTAMP(6),?)", bom, actor); Long version = ownId("bom_version", "bom_version_id");
        jdbc.update("INSERT INTO bom_component(bom_version_id,material_item_id,line_no,required_qty,material_unit_code,created_by) VALUES (?,?,1,1,'EA',?)", version, rawId, actor); Long component = ownId("bom_component", "bom_component_id");
        jdbc.update("INSERT INTO routing(product_item_id,routing_code,created_by) VALUES (?,?,?)", product, actor, actor); Long routing = ownId("routing", "routing_id");
        jdbc.update("INSERT INTO routing_revision(routing_id,revision_no,first_used_at,created_by) VALUES (?,1,UTC_TIMESTAMP(6),?)", routing, actor); Long revision = ownId("routing_revision", "routing_revision_id");
        jdbc.update("INSERT INTO work_order(production_plan_id,product_item_id,bom_version_id,routing_revision_id,ordered_qty,product_unit_code,status,started_at,started_by,created_by) VALUES (?,?,?,?,100,'EA','IN_PROGRESS',UTC_TIMESTAMP(6),?,?)", plan, product, version, revision, actor, actor);
        Long workOrder = ownId("work_order", "work_order_id");
        Long receipt1 = receipt("C1", "MATERIAL_CONSUME"), receipt2 = receipt("C2", "MATERIAL_CONSUME");
        jdbc.update("INSERT INTO material_consumption(work_order_id,raw_lot_id,bom_component_id,receipt_id,consumed_qty,consumed_at,created_by) VALUES (?,?,?,?,40,UTC_TIMESTAMP(6),?)", workOrder, lotId, component, receipt1, actor);
        jdbc.update("INSERT INTO material_consumption(work_order_id,raw_lot_id,bom_component_id,receipt_id,consumed_qty,consumed_at,created_by) VALUES (?,?,?,?,20,UTC_TIMESTAMP(6),?)", workOrder, lotId, component, receipt2, actor);
        Long consumption = jdbc.queryForObject("SELECT consumption_id FROM material_consumption WHERE receipt_id=?", Long.class, receipt1);
        for (String key : new String[]{"R1", "R2"}) {
            jdbc.update("INSERT INTO material_return(consumption_id,receipt_id,returned_qty,returned_at,reason,created_by) VALUES (?,?,10,UTC_TIMESTAMP(6),'테스트 실제 반환',?)", consumption, receipt(key, "MATERIAL_RETURN"), actor);
        }
        jdbc.update("UPDATE raw_material_lot SET available_qty=60 WHERE raw_lot_id=?", lotId);
        RawMaterialLot lot = service.get(lotId);
        assertThat(lot.getConsumedQty()).isEqualByComparingTo("60"); assertThat(lot.getReturnedQty()).isEqualByComparingTo("20");
        assertThat(lot.getNetConsumedQty()).isEqualByComparingTo("40"); assertThat(lot.isQuantityConsistent()).isTrue();
        jdbc.update("UPDATE raw_material_lot SET available_qty=59 WHERE raw_lot_id=?", lotId);
        assertThat(service.get(lotId).isQuantityConsistent()).isFalse();
        assertThat(jdbc.queryForObject("SELECT available_qty FROM raw_material_lot WHERE raw_lot_id=?", BigDecimal.class, lotId)).isEqualByComparingTo("59");
    }

    private int duplicateOutcome(RawMaterialLotService writer, Long itemId) {
        try { writer.create(form(itemId, actor, "10")); return 1; }
        catch (RawMaterialLotRuleException exception) { assertThat(exception.getField()).isEqualTo("lotNo"); return 0; }
    }
    private Long item(String suffix, String kind, String unit) {
        jdbc.update("INSERT INTO item(item_code,item_name,item_kind,unit_code,is_active,created_by) VALUES (?,?,?, ?,1,?)", actor+suffix, "원자재 테스트", kind, unit, actor);
        return jdbc.queryForObject("SELECT item_id FROM item WHERE item_code=?", Long.class, actor+suffix);
    }
    private Long receipt(String key, String operation) {
        jdbc.update("INSERT INTO request_receipt(scope_key,operation_code,request_key,payload_fingerprint,initial_outcome_snapshot,processed_at,created_by) VALUES (?,?,?,UNHEX(SHA2('fixture',256)),'{}',UTC_TIMESTAMP(6),?)", actor, operation, key, actor);
        return jdbc.queryForObject("SELECT receipt_id FROM request_receipt WHERE scope_key=? AND operation_code=? AND request_key=?", Long.class, actor, operation, key);
    }
    private Long ownId(String table, String column) { return jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE created_by=?", Long.class, actor); }
    private RawMaterialLotService transactional(RawMaterialLotMapper selected) {
        var target = new RawMaterialLotService(selected, items, new EnvironmentActorProvider(actor));
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (RawMaterialLotService) proxy.getProxy();
    }
    private RawMaterialLotForm form(Long itemId, String number, String quantity) {
        var form = new RawMaterialLotForm(); form.setItemId(itemId); form.setLotNo(number); form.setRegisteredQty(new BigDecimal(quantity)); return form;
    }
    private void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test latch timeout."); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }
    private String env(String name) {
        String value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing dedicated test variable: " + name); return value;
    }
}
