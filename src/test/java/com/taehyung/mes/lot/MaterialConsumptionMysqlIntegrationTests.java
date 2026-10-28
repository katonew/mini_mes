package com.taehyung.mes.lot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taehyung.mes.bom.BomMapper;
import com.taehyung.mes.common.actor.EnvironmentActorProvider;
import com.taehyung.mes.workorder.WorkOrderMapper;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 기존 schema.sql을 사전에 적용한 로컬 전용 DB만 opt-in으로 사용한다. 일반 DB_*는 읽지 않는다. */
@EnabledIfEnvironmentVariable(named="MES_CONSUME_MYSQL_TEST", matches="LOCAL_TEST_ONLY")
class MaterialConsumptionMysqlIntegrationTests {
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private MaterialConsumptionMapper mapper;
    private WorkOrderMapper orders;
    private BomMapper boms;
    private MaterialConsumptionOutcomeCodec codec;
    private MaterialConsumptionService service;
    private String actor;
    private Long planId, productId, rawId, lotId, versionId, componentId, revisionId, orderId;

    @BeforeEach void setUp() throws Exception {
        String database = env("CONSUME_TEST_DB_NAME");
        if (!database.matches("[a-z0-9_]+_consume_test")) throw new IllegalArgumentException("Use a dedicated *_consume_test schema.");
        int port = Integer.parseInt(System.getenv().getOrDefault("CONSUME_TEST_DB_PORT", "3306"));
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid local test port.");
        var source = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true", env("CONSUME_TEST_DB_USER"), env("CONSUME_TEST_DB_PASSWORD"));
        jdbc = new JdbcTemplate(source); assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(database);
        transactions = new DataSourceTransactionManager(source); transactions.setDefaultTimeout(10);
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(source); var config = new Configuration(); config.setMapUnderscoreToCamelCase(true); factory.setConfiguration(config);
        var resolver = new PathMatchingResourcePatternResolver();
        factory.setMapperLocations(resolver.getResource("classpath:mappers/lot/MaterialConsumptionMapper.xml"), resolver.getResource("classpath:mappers/workorder/WorkOrderMapper.xml"), resolver.getResource("classpath:mappers/bom/BomMapper.xml"));
        factory.afterPropertiesSet(); var sessions = new SqlSessionTemplate(factory.getObject());
        mapper = sessions.getMapper(MaterialConsumptionMapper.class); orders = sessions.getMapper(WorkOrderMapper.class); boms = sessions.getMapper(BomMapper.class);
        codec = new MaterialConsumptionOutcomeCodec(new ObjectMapper().findAndRegisterModules()); actor = "CON_IT_" + UUID.randomUUID();
        productId = item("P", "FINISHED"); rawId = item("R", "RAW");
        jdbc.update("INSERT INTO raw_material_lot(item_id,lot_no,unit_code,registered_qty,available_qty,registered_at,created_by) VALUES (?,?,'EA',100,100,UTC_TIMESTAMP(6),?)", rawId, actor, actor); lotId = ownId("raw_material_lot", "raw_lot_id");
        jdbc.update("INSERT INTO production_plan(product_item_id,planned_qty,planned_date,created_by) VALUES (?,10,'2026-10-27',?)", productId, actor); planId = ownId("production_plan", "production_plan_id");
        jdbc.update("INSERT INTO bom(product_item_id,bom_code,created_by) VALUES (?,?,?)", productId, actor, actor); Long bomId = ownId("bom", "bom_id");
        jdbc.update("INSERT INTO bom_version(bom_id,version_no,base_qty,product_unit_code,first_used_at,created_by) VALUES (?,1,1,'EA',UTC_TIMESTAMP(6),?)", bomId, actor); versionId = ownId("bom_version", "bom_version_id");
        jdbc.update("INSERT INTO bom_component(bom_version_id,material_item_id,line_no,required_qty,material_unit_code,created_by) VALUES (?,?,1,1,'EA',?)", versionId, rawId, actor); componentId = ownId("bom_component", "bom_component_id");
        jdbc.update("INSERT INTO routing(product_item_id,routing_code,created_by) VALUES (?,?,?)", productId, actor, actor); Long routingId = ownId("routing", "routing_id");
        jdbc.update("INSERT INTO routing_revision(routing_id,revision_no,first_used_at,created_by) VALUES (?,1,UTC_TIMESTAMP(6),?)", routingId, actor); revisionId = ownId("routing_revision", "routing_revision_id");
        orderId = order(); service = service(mapper);
    }
    @AfterEach void cleanOwnFixtures() {
        if (actor == null) return;
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            for (String table : new String[]{"material_consumption", "request_receipt", "raw_material_lot", "work_order", "bom_component", "bom_version", "bom", "routing_revision", "routing", "production_plan", "item"}) {
                jdbc.update("DELETE FROM " + table + " WHERE created_by=?", actor);
            }
        });
    }
    @Test void consumptionPersistsNormalizedQuantityUtcMicrosecondsAndCompletedReceipt() {
        String key = key(); var before = jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class);
        var result = service.create(form(orderId, "1.000000", key)); var after = jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class);
        assertThat(result.consumedAt()).isBetween(before, after); assertThat(available()).isEqualByComparingTo("99");
        assertThat(result.availableBefore()).isEqualTo("100"); assertThat(result.availableAfter()).isEqualTo("99");
        assertThat(service.get(result.consumptionId()).getOutcome()).isEqualTo(result);
        assertThat(service.list(orderId.toString(), actor, "자재")).hasSize(1);
        assertThat(service.options(orderId, componentId).getMaterials().get(0).getNetConsumedQty()).isEqualByComparingTo("1");
        assertThat(service.create(form(orderId, "1", key))).isEqualTo(result); assertCounts(1);
        var receipt = mapper.findReceipt(commandScope(key), key);
        assertThat(receipt.getProcessedAt()).isNotNull(); assertThat(receipt.getSnapshot()).contains("availableBefore", "availableAfter");
        assertThat(receipt.getPayloadFingerprint()).hasSize(32);
    }
    @Test void expectedBomQuantityDoesNotCapActualConsumptionAndZeroStockIsValid() {
        assertThat(service.options(orderId, componentId).getMaterials().get(0).getExpectedQtyDisplay()).isEqualTo("10");
        var result = service.create(form(orderId, "100", key()));
        assertThat(result.consumedQty()).isEqualTo("100"); assertThat(available()).isEqualByComparingTo("0"); assertCounts(1);
    }
    @Test void replayAfterSubsequentConsumptionAndClosedStatesReturnsOriginalBalances() {
        String key = key(); var original = service.create(form(orderId, "1", key)); service.create(form(orderId, "2", key()));
        for (String state : new String[]{"COMPLETED", "CANCELLED"}) {
            closedState(state); assertThat(service.create(form(orderId, "1.000000", key))).isEqualTo(original);
        }
        assertThat(original.availableAfter()).isEqualTo("99"); assertThat(available()).isEqualByComparingTo("97"); assertCounts(2);
        assertThatThrownBy(() -> service.create(form(orderId, "2", key))).isInstanceOf(MaterialConsumptionRuleException.class).hasMessageContaining("다른");
        assertThat(available()).isEqualByComparingTo("97"); assertCounts(2);
    }
    @Test void simultaneousSameRequestWaitsForCommitAndReplaysWithoutSecondDecrement() throws Exception {
        String key = key(); var claimed = new CountDownLatch(1); var release = new CountDownLatch(1); var started = new CountDownLatch(1);
        var paused = spy(mapper); doAnswer(call -> { int count = mapper.claimReceipt(call.getArgument(0)); claimed.countDown(); await(release); return count; }).when(paused).claimReceipt(any());
        var firstService = service(paused); var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> firstService.create(form(orderId, "30", key)));
            assertThat(claimed.await(5, TimeUnit.SECONDS)).isTrue();
            assertCounts(0); assertThat(available()).isEqualByComparingTo("100");
            var second = pool.submit(() -> { started.countDown(); return service.create(form(orderId, "30.000000", key)); });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); assertThat(first.get(12, TimeUnit.SECONDS)).isEqualTo(second.get(12, TimeUnit.SECONDS));
            assertCounts(1); assertThat(available()).isEqualByComparingTo("70");
        } finally { release.countDown(); stop(pool); }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void differentRequestsCannotOverdrawOneLotWithinOrAcrossWorkOrders(boolean differentOrders) throws Exception {
        Long otherOrder = differentOrders ? order() : orderId;
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1); var started = new CountDownLatch(1);
        var paused = spy(mapper); doAnswer(call -> { var lot = mapper.findLotForUpdate(call.getArgument(0)); locked.countDown(); await(release); return lot; }).when(paused).findLotForUpdate(lotId);
        var firstService = service(paused); var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> firstService.create(form(orderId, "60", key())));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> { started.countDown(); return service.create(form(otherOrder, "60", key())); });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); first.get(12, TimeUnit.SECONDS);
            assertThatThrownBy(() -> second.get(12, TimeUnit.SECONDS)).hasCauseInstanceOf(MaterialConsumptionRuleException.class);
            assertCounts(1); assertThat(available()).isEqualByComparingTo("40");
        } finally { release.countDown(); stop(pool); }
    }
    @Test void shortageLeavesNoReceiptOrConsumptionAndSameKeyCanSucceedAfterRollback() {
        String key = key(); assertThatThrownBy(() -> service.create(form(orderId, "101", key))).isInstanceOf(MaterialConsumptionRuleException.class);
        assertEmpty(); service.create(form(orderId, "1", key)); assertCounts(1); assertThat(available()).isEqualByComparingTo("99");
    }
    @ParameterizedTest @ValueSource(strings={"AFTER_CONSUMPTION", "RECEIPT_CHECK", "AFTER_RECEIPT"})
    void failuresAtEachWritePhaseRollBackAllThreeTables(String phase) {
        var failing = spy(mapper); var reached = new java.util.concurrent.atomic.AtomicBoolean();
        if (phase.equals("AFTER_CONSUMPTION")) {
            doAnswer(call -> { MaterialConsumption row = call.getArgument(0); mapper.insert(row); reached.set(true);
                jdbc.update("UPDATE material_consumption SET consumed_qty=-1 WHERE consumption_id=?", row.getConsumptionId()); return 1;
            }).when(failing).insert(any());
        } else {
            doAnswer(call -> {
                Long receiptId = call.getArgument(0); reached.set(true);
                if (phase.equals("RECEIPT_CHECK")) { jdbc.update("UPDATE request_receipt SET initial_outcome_snapshot='{}',processed_at=NULL WHERE receipt_id=?", receiptId); }
                mapper.finishReceipt(receiptId, call.getArgument(1)); throw new DataIntegrityViolationException("Injected failure after receipt finish.");
            }).when(failing).finishReceipt(anyLong(), anyString());
        }
        Throwable failure = catchThrowable(() -> service(failing).create(form(orderId, "10", key())));
        assertThat(reached.get()).isTrue(); assertThat(failure).isInstanceOf(DataAccessException.class); assertEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"COMPLETED", "CANCELLED"})
    void stateChangeUnderWorkOrderLockRejectsWaitingNewConsumption(String state) throws Exception {
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1); var started = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var holder = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> { orders.findByIdForUpdate(orderId); locked.countDown(); await(release); closedState(state); }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var writer = pool.submit(() -> { started.countDown(); return service.create(form(orderId, "1", key())); });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> writer.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); holder.get(12, TimeUnit.SECONDS);
            assertThatThrownBy(() -> writer.get(12, TimeUnit.SECONDS)).hasCauseInstanceOf(MaterialConsumptionRuleException.class); assertEmpty();
        } finally { release.countDown(); stop(pool); }
    }
    @Test void actualMysqlLockWaitTimeoutRollsBackReceiptInsteadOfPartialSuccess() throws Exception {
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1); var pool = Executors.newSingleThreadExecutor();
        var timed = spy(mapper); doAnswer(call -> { jdbc.execute("SET SESSION innodb_lock_wait_timeout=1"); return mapper.claimReceipt(call.getArgument(0)); }).when(timed).claimReceipt(any());
        try {
            var holder = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> { mapper.findLotForUpdate(lotId); locked.countDown(); await(release); }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            Throwable failure = catchThrowable(() -> service(timed).create(form(orderId, "1", key())));
            assertThat(failure).isInstanceOf(DataAccessException.class);
            Throwable cause = failure; while (cause != null && !(cause instanceof java.sql.SQLException)) { cause = cause.getCause(); }
            assertThat(cause).isInstanceOf(java.sql.SQLException.class); assertThat(((java.sql.SQLException) cause).getErrorCode()).isEqualTo(1205);
            release.countDown(); holder.get(12, TimeUnit.SECONDS); assertEmpty();
        } finally { release.countDown(); stop(pool); }
    }
    private MaterialConsumptionService service(MaterialConsumptionMapper selected) {
        var target = new MaterialConsumptionTransactionService(selected, orders, boms, codec); var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return new MaterialConsumptionService(mapper, (MaterialConsumptionTransactionService) proxy.getProxy(), new EnvironmentActorProvider(actor), codec);
    }
    private Long item(String suffix, String kind) {
        jdbc.update("INSERT INTO item(item_code,item_name,item_kind,unit_code,is_active,created_by) VALUES (?,?,?,'EA',1,?)", actor+suffix, "소비 테스트 자재", kind, actor);
        return jdbc.queryForObject("SELECT item_id FROM item WHERE item_code=?", Long.class, actor+suffix);
    }
    private Long order() {
        jdbc.update("INSERT INTO work_order(production_plan_id,product_item_id,bom_version_id,routing_revision_id,ordered_qty,product_unit_code,status,started_at,started_by,created_by) VALUES (?,?,?,?,10,'EA','IN_PROGRESS',UTC_TIMESTAMP(6),?,?)", planId, productId, versionId, revisionId, actor, actor);
        return jdbc.queryForObject("SELECT MAX(work_order_id) FROM work_order WHERE created_by=?", Long.class, actor);
    }
    private void closedState(String state) {
        if (state.equals("COMPLETED")) {
            jdbc.update("UPDATE work_order SET status='COMPLETED',started_at=UTC_TIMESTAMP(6),started_by=?,completed_at=UTC_TIMESTAMP(6),completed_by=?,inspection_target_qty=10,cancelled_at=NULL,cancelled_by=NULL WHERE work_order_id=?", actor, actor, orderId);
        } else {
            jdbc.update("UPDATE work_order SET status='CANCELLED',started_at=NULL,started_by=NULL,completed_at=NULL,completed_by=NULL,inspection_target_qty=NULL,cancelled_at=UTC_TIMESTAMP(6),cancelled_by=? WHERE work_order_id=?", actor, orderId);
        }
    }
    private MaterialConsumptionForm form(Long order, String quantity, String key) {
        var form = new MaterialConsumptionForm(); form.setWorkOrderId(order); form.setBomComponentId(componentId); form.setRawLotId(lotId); form.setConsumedQty(new BigDecimal(quantity)); form.setRequestKey(key); return form;
    }
    private String commandScope(String key) { return MaterialConsumptionCommand.from(form(orderId, "1", key), actor).scopeKey(); }
    private BigDecimal available() { return jdbc.queryForObject("SELECT available_qty FROM raw_material_lot WHERE raw_lot_id=?", BigDecimal.class, lotId); }
    private void assertCounts(long count) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM material_consumption WHERE created_by=?", Long.class, actor)).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM request_receipt WHERE created_by=?", Long.class, actor)).isEqualTo(count);
    }
    private void assertEmpty() { assertCounts(0); assertThat(available()).isEqualByComparingTo("100"); }
    private Long ownId(String table, String column) { return jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE created_by=?", Long.class, actor); }
    private String key() { return UUID.randomUUID().toString(); }
    private String env(String name) { String value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing dedicated test variable: " + name); return value; }
    private void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test latch timeout."); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }
    private void stop(java.util.concurrent.ExecutorService pool) throws InterruptedException { pool.shutdownNow(); if (!pool.awaitTermination(12, TimeUnit.SECONDS)) throw new IllegalStateException("Test threads did not stop."); }
}
