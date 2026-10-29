package com.taehyung.mes.production;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taehyung.mes.common.actor.EnvironmentActorProvider;
import com.taehyung.mes.workorder.WorkOrderMapper;
import com.taehyung.mes.workorder.WorkOrderRuleException;
import com.taehyung.mes.workorder.WorkOrderService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.IsolationLevelDataSourceAdapter;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** schema.sql이 사전에 적용된 로컬 전용 mini_mes_result_test에서만 명시적으로 실행한다. */
@EnabledIfEnvironmentVariable(named="MES_RESULT_MYSQL_TEST", matches="LOCAL_TEST_ONLY")
class ProductionResultMysqlIntegrationTests {
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private ProductionResultMapper mapper;
    private WorkOrderMapper orders;
    private ProductionResultOutcomeCodec codec;
    private ProductionResultService service;
    private WorkOrderService workOrders;
    private String actor;
    private Long productId, planId, versionId, revisionId, orderId;

    @BeforeEach void setUp() throws Exception {
        String database = env("RESULT_TEST_DB_NAME");
        if (!"mini_mes_result_test".equals(database)) { throw new IllegalArgumentException("Use only mini_mes_result_test."); }
        int port = Integer.parseInt(System.getenv().getOrDefault("RESULT_TEST_DB_PORT", "3306"));
        if (port != 3306) { throw new IllegalArgumentException("Use the local MySQL80 port 3306."); }
        var physical = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/" + database
                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true", env("RESULT_TEST_DB_USER"), env("RESULT_TEST_DB_PASSWORD"));
        var source = new IsolationLevelDataSourceAdapter(); source.setTargetDataSource(physical);
        source.setIsolationLevel(java.sql.Connection.TRANSACTION_READ_COMMITTED);
        jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(database);
        transactions = new DataSourceTransactionManager(source); transactions.setDefaultTimeout(10);
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
        var config = new Configuration(); config.setMapUnderscoreToCamelCase(true); factory.setConfiguration(config);
        var resources = new PathMatchingResourcePatternResolver();
        factory.setMapperLocations(resources.getResource("classpath:mappers/production/ProductionResultMapper.xml"),
                resources.getResource("classpath:mappers/workorder/WorkOrderMapper.xml"));
        factory.afterPropertiesSet(); var session = new SqlSessionTemplate(factory.getObject());
        mapper = session.getMapper(ProductionResultMapper.class); orders = session.getMapper(WorkOrderMapper.class);
        codec = new ProductionResultOutcomeCodec(new ObjectMapper().findAndRegisterModules());
        actor = "RESULT_IT_" + UUID.randomUUID();
        jdbc.update("INSERT INTO item(item_code,item_name,item_kind,unit_code,is_active,created_by) VALUES (?,?,'FINISHED','EA',1,?)", actor+"P", "통합 제품", actor);
        productId = ownId("item", "item_id");
        jdbc.update("INSERT INTO production_plan(product_item_id,planned_qty,planned_date,created_by) VALUES (?,10,'2026-10-28',?)", productId, actor);
        planId = ownId("production_plan", "production_plan_id");
        jdbc.update("INSERT INTO bom(product_item_id,bom_code,created_by) VALUES (?,?,?)", productId, actor, actor);
        Long bomId = ownId("bom", "bom_id");
        jdbc.update("INSERT INTO bom_version(bom_id,version_no,base_qty,product_unit_code,created_by) VALUES (?,1,1,'EA',?)", bomId, actor);
        versionId = ownId("bom_version", "bom_version_id");
        jdbc.update("INSERT INTO routing(product_item_id,routing_code,created_by) VALUES (?,?,?)", productId, actor, actor);
        Long routingId = ownId("routing", "routing_id");
        jdbc.update("INSERT INTO routing_revision(routing_id,revision_no,created_by) VALUES (?,1,?)", routingId, actor);
        revisionId = ownId("routing_revision", "routing_revision_id");
        orderId = newOrder(); service = service(mapper);
        workOrders = transactional(new WorkOrderService(orders, null, null, null, null, new EnvironmentActorProvider(actor)));
    }
    @AfterEach void cleanOwnFixtures() {
        if (actor == null || transactions == null) return;
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("DELETE FROM production_result WHERE created_by=?", actor);
            jdbc.update("DELETE FROM finished_goods_lot WHERE created_by=?", actor);
            jdbc.update("DELETE FROM request_receipt WHERE created_by=?", actor);
            jdbc.update("DELETE FROM work_order WHERE created_by=?", actor);
            jdbc.update("DELETE FROM bom_version WHERE created_by=?", actor);
            jdbc.update("DELETE FROM bom WHERE created_by=?", actor);
            jdbc.update("DELETE FROM routing_revision WHERE created_by=?", actor);
            jdbc.update("DELETE FROM routing WHERE created_by=?", actor);
            jdbc.update("DELETE FROM production_plan WHERE created_by=?", actor);
            jdbc.update("DELETE FROM item WHERE created_by=?", actor);
        });
    }
    @Test void firstAndLaterResultsShareOneLotAndCompletionCapturesGoodQuantity() {
        var before = jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class);
        var first = service.create(form(orderId, actor+"-FG", "4", "3", "1", key()));
        assertThat(first.producedAt()).isBetween(before, jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", LocalDateTime.class));
        assertThat(service.get(first.productionResultId()).getOutcome()).isEqualTo(first);
        var later = service.create(form(orderId, null, "6", "5", "1", key()));
        assertThat(later.finishedLotId()).isEqualTo(first.finishedLotId());
        assertThat(count("finished_goods_lot")).isEqualTo(1L); assertThat(count("production_result")).isEqualTo(2L);
        assertThat(orders.findTotals(orderId).getProducedQty()).isEqualByComparingTo("10");
        assertThat(service.list(orderId.toString(), actor, "통합")).hasSize(2);
        workOrders.complete(orderId);
        var completed = orders.findById(orderId);
        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
        assertThat(completed.getInspectionTargetQty()).isEqualByComparingTo("8");
        assertThat(completed.getCompletedAt()).isNotNull(); assertThat(completed.getCompletedBy()).isEqualTo(actor);
        assertThatThrownBy(() -> service.create(form(orderId, null, "1", "1", "0", key())))
                .isInstanceOf(ProductionResultRuleException.class);
    }
    @Test void sameUuidReplaysOriginalResultAfterCompletionAndChangedPayloadIsRejected() {
        String key = key(); var input = form(orderId, actor+"-FG", "10.000000", "9", "1", key);
        var first = service.create(input); workOrders.complete(orderId);
        assertThat(service.create(form(orderId, actor+"-FG", "10", "9.000000", "1", key))).isEqualTo(first);
        assertThatThrownBy(() -> service.create(form(orderId, actor+"-FG", "10", "8", "2", key)))
                .isInstanceOf(ProductionResultRuleException.class);
        assertThat(count("production_result")).isEqualTo(1L);
        assertThat(count("request_receipt")).isEqualTo(1L);
    }
    @Test void duplicateLotAcrossOrdersAndOverproductionRollbackTheirReceipts() {
        service.create(form(orderId, actor+"-FG", "4", "4", "0", key()));
        Long other = newOrder();
        assertThatThrownBy(() -> service.create(form(other, actor+"-FG", "1", "1", "0", key())))
                .isInstanceOf(ProductionResultRuleException.class);
        assertThatThrownBy(() -> service.create(form(orderId, null, "7", "7", "0", key())))
                .isInstanceOf(ProductionResultRuleException.class);
        assertThat(count("finished_goods_lot")).isEqualTo(1L);
        assertThat(count("production_result")).isEqualTo(1L);
        assertThat(count("request_receipt")).isEqualTo(1L);
    }
    @Test void resultInsertFailureRollsBackLotAndReceipt() {
        var broken = spy(mapper);
        doThrow(new DataIntegrityViolationException("injected result failure")).when(broken).insert(any());
        var failing = service(broken);
        assertThatThrownBy(() -> failing.create(form(orderId, actor+"-FG", "4", "4", "0", key())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertEmptyLedger();
    }
    @Test void receiptCompletionFailureRollsBackResultAndLot() {
        var broken = spy(mapper);
        doThrow(new DataIntegrityViolationException("injected receipt failure")).when(broken).finishReceipt(anyLong(), anyString());
        var failing = service(broken);
        assertThatThrownBy(() -> failing.create(form(orderId, actor+"-FG", "4", "4", "0", key())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertEmptyLedger();
    }
    @Test void concurrentFirstRequestsCannotCreateTwoLots() throws Exception {
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> attempt(() -> service.create(form(orderId, actor+"-A", "4", "4", "0", key())), start));
            var b = pool.submit(() -> attempt(() -> service.create(form(orderId, actor+"-B", "5", "5", "0", key())), start));
            start.countDown();
            long success = (a.get(12, TimeUnit.SECONDS) == null ? 1 : 0) + (b.get(12, TimeUnit.SECONDS) == null ? 1 : 0);
            assertThat(success).isEqualTo(1);
            assertThat(count("finished_goods_lot")).isEqualTo(1L);
            assertThat(count("production_result")).isEqualTo(1L);
            assertThat(count("request_receipt")).isEqualTo(1L);
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(12, TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void completionAndFinalResultRaceKeepsStateAndQuantityConsistent() throws Exception {
        service.create(form(orderId, actor+"-FG", "6", "5", "1", key()));
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var added = pool.submit(() -> attempt(() -> service.create(form(orderId, null, "4", "4", "0", key())), start));
            var completed = pool.submit(() -> attempt(() -> workOrders.complete(orderId), start));
            start.countDown();
            Throwable addError = added.get(12, TimeUnit.SECONDS), completeError = completed.get(12, TimeUnit.SECONDS);
            if (addError != null) assertThat(addError).isInstanceOf(ProductionResultRuleException.class);
            if (completeError != null) assertThat(completeError).isInstanceOf(WorkOrderRuleException.class);
            var current = orders.findById(orderId); var totals = orders.findTotals(orderId);
            assertThat(totals.getProducedQty()).isLessThanOrEqualTo(new BigDecimal("10"));
            if ("COMPLETED".equals(current.getStatus())) {
                assertThat(totals.getProducedQty()).isEqualByComparingTo("10");
                assertThat(current.getInspectionTargetQty()).isEqualByComparingTo(totals.getGoodQty());
            } else { assertThat(current.getStatus()).isEqualTo("IN_PROGRESS"); }
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(12, TimeUnit.SECONDS)).isTrue(); }
    }
    private ProductionResultService service(ProductionResultMapper selected) {
        var writer = transactional(new ProductionResultTransactionService(selected, orders, codec));
        return new ProductionResultService(mapper, writer, codec, orders, new EnvironmentActorProvider(actor));
    }
    @SuppressWarnings("unchecked") private <T> T transactional(T target) {
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }
    private Long newOrder() {
        jdbc.update("INSERT INTO work_order(production_plan_id,product_item_id,bom_version_id,routing_revision_id,ordered_qty,product_unit_code,status,started_at,started_by,created_by) VALUES (?,?,?,?,10,'EA','IN_PROGRESS',UTC_TIMESTAMP(6),?,?)",
                planId, productId, versionId, revisionId, actor, actor);
        return ownId("work_order", "work_order_id");
    }
    private ProductionResultForm form(Long order, String lotNo, String produced, String good, String defect, String key) {
        var form = new ProductionResultForm(); form.setWorkOrderId(order); form.setLotNo(lotNo);
        form.setProducedQty(new BigDecimal(produced)); form.setGoodQty(new BigDecimal(good)); form.setDefectQty(new BigDecimal(defect));
        form.setWorkerRef("WORKER"); form.setRequestKey(key); return form;
    }
    private Throwable attempt(Runnable work, CountDownLatch start) {
        try { if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Latch timeout."); work.run(); return null; }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); return exception; }
        catch (RuntimeException exception) { return exception; }
    }
    private void assertEmptyLedger() {
        assertThat(count("finished_goods_lot")).isZero(); assertThat(count("production_result")).isZero();
        assertThat(count("request_receipt")).isZero();
    }
    private Long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE created_by=?", Long.class, actor); }
    private Long ownId(String table, String column) {
        return jdbc.queryForObject("SELECT MAX(" + column + ") FROM " + table + " WHERE created_by=?", Long.class, actor);
    }
    private String key() { return UUID.randomUUID().toString(); }
    private String env(String name) {
        String value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing dedicated test variable: " + name);
        return value;
    }
}
