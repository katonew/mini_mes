package com.taehyung.mes.workorder;

import com.taehyung.mes.bom.*;
import com.taehyung.mes.routing.RoutingMapper;
import com.taehyung.mes.item.ItemMapper;
import com.taehyung.mes.process.ProcessMapper;
import com.taehyung.mes.common.actor.EnvironmentActorProvider;
import java.math.BigDecimal;
import java.util.List;
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
import org.springframework.jdbc.datasource.IsolationLevelDataSourceAdapter;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 전용 로컬 스키마만 명시적 opt-in으로 실행. 기본 DB 설정과 application.yml은 사용하지 않는다. */
@EnabledIfEnvironmentVariable(named="MES_WO_MYSQL_TEST", matches="LOCAL_TEST_ONLY")
class WorkOrderMysqlIntegrationTests {
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private WorkOrderMapper mapper;
    private BomMapper boms;
    private RoutingMapper routings;
    private ItemMapper items;
    private ProcessMapper processes;
    private WorkOrderService service;
    private String actor;
    private Long planId, productId, rawId, bomId, versionId, routingId, revisionId, processId;

    @BeforeEach void setUp() throws Exception {
        String database = env("WO_TEST_DB_NAME");
        if (!database.matches("[a-z0-9_]+_wo_test")) throw new IllegalArgumentException("Use a dedicated *_wo_test schema.");
        int port = Integer.parseInt(System.getenv().getOrDefault("WO_TEST_DB_PORT", "3306"));
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid test port.");
        var physical = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true", env("WO_TEST_DB_USER"), env("WO_TEST_DB_PASSWORD"));
        var source = new IsolationLevelDataSourceAdapter(); source.setTargetDataSource(physical);
        source.setIsolationLevel(java.sql.Connection.TRANSACTION_READ_COMMITTED);
        jdbc = new JdbcTemplate(source); assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(database);
        assertThat(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Integer>) connection -> connection.getTransactionIsolation()))
                .isEqualTo(java.sql.Connection.TRANSACTION_READ_COMMITTED);
        transactions = new DataSourceTransactionManager(source); transactions.setDefaultTimeout(10);
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
        var config = new Configuration(); config.setMapUnderscoreToCamelCase(true); factory.setConfiguration(config);
        var resolver = new PathMatchingResourcePatternResolver();
        factory.setMapperLocations(resolver.getResource("classpath:mappers/workorder/WorkOrderMapper.xml"), resolver.getResource("classpath:mappers/bom/BomMapper.xml"),
                resolver.getResource("classpath:mappers/routing/RoutingMapper.xml"), resolver.getResource("classpath:mappers/item/ItemMapper.xml"), resolver.getResource("classpath:mappers/process/ProcessMapper.xml"));
        factory.afterPropertiesSet(); var sessions = new SqlSessionTemplate(factory.getObject());
        mapper = sessions.getMapper(WorkOrderMapper.class); boms = sessions.getMapper(BomMapper.class); routings = sessions.getMapper(RoutingMapper.class);
        items = sessions.getMapper(ItemMapper.class); processes = sessions.getMapper(ProcessMapper.class);
        actor = "WO_IT_" + UUID.randomUUID();
        jdbc.update("INSERT INTO item(item_code,item_name,item_kind,unit_code,is_active,created_by) VALUES (?,?,'FINISHED','EA',1,?)", actor+"-P", "지시 통합 제품", actor);
        productId = jdbc.queryForObject("SELECT item_id FROM item WHERE item_code=?", Long.class, actor+"-P");
        jdbc.update("INSERT INTO item(item_code,item_name,item_kind,unit_code,is_active,created_by) VALUES (?,?,'RAW','EA',1,?)", actor+"-R", "지시 통합 자재", actor);
        rawId = jdbc.queryForObject("SELECT item_id FROM item WHERE item_code=?", Long.class, actor+"-R");
        jdbc.update("INSERT INTO production_plan(product_item_id,planned_qty,planned_date,created_by) VALUES (?,100,'2026-11-01',?)", productId, actor);
        planId = ownId("SELECT production_plan_id FROM production_plan WHERE created_by=?");
        jdbc.update("INSERT INTO bom(product_item_id,bom_code,created_by) VALUES (?,?,?)", productId, actor, actor); bomId = ownId("SELECT bom_id FROM bom WHERE created_by=?");
        jdbc.update("INSERT INTO bom_version(bom_id,version_no,base_qty,product_unit_code,created_by) VALUES (?,1,1,'EA',?)", bomId, actor); versionId = ownId("SELECT bom_version_id FROM bom_version WHERE created_by=?");
        jdbc.update("INSERT INTO bom_component(bom_version_id,material_item_id,line_no,required_qty,material_unit_code,created_by) VALUES (?,?,1,1,'EA',?)", versionId, rawId, actor);
        jdbc.update("INSERT INTO manufacturing_process(process_code,process_name,is_active,created_by) VALUES (?, ?,1,?)", actor, "현재 공정", actor); processId = ownId("SELECT process_id FROM manufacturing_process WHERE created_by=?");
        jdbc.update("INSERT INTO routing(product_item_id,routing_code,created_by) VALUES (?,?,?)", productId, actor, actor); routingId = ownId("SELECT routing_id FROM routing WHERE created_by=?");
        jdbc.update("INSERT INTO routing_revision(routing_id,revision_no,created_by) VALUES (?,1,?)", routingId, actor); revisionId = ownId("SELECT routing_revision_id FROM routing_revision WHERE created_by=?");
        jdbc.update("INSERT INTO routing_step(routing_revision_id,process_id,step_no,process_name_snapshot,created_by) VALUES (?,?,10,?,?)", revisionId, processId, "저장 당시 공정", actor);
        service = transactional(new WorkOrderService(mapper, boms, routings, items, processes, new EnvironmentActorProvider(actor)));
    }
    @AfterEach void cleanOwnFixtures() {
        if (actor == null) return;
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("DELETE FROM work_order WHERE created_by=?", actor);
            jdbc.update("DELETE FROM routing_step WHERE created_by=?", actor); jdbc.update("DELETE FROM routing_revision WHERE created_by=?", actor); jdbc.update("DELETE FROM routing WHERE created_by=?", actor);
            jdbc.update("DELETE FROM bom_component WHERE created_by=?", actor); jdbc.update("DELETE FROM bom_version WHERE created_by=?", actor); jdbc.update("DELETE FROM bom WHERE created_by=?", actor);
            jdbc.update("DELETE FROM manufacturing_process WHERE created_by=?", actor); jdbc.update("DELETE FROM production_plan WHERE created_by=?", actor); jdbc.update("DELETE FROM item WHERE created_by=?", actor);
        });
    }
    @Test void exactReferencesAndExistingTimestampsSurviveMultipleOrdersAndProcessDeactivation() {
        Long id = service.create(form()); String bomTime = bomTime(); String routingTime = routingTime();
        assertThat(bomTime).isNotNull(); assertThat(routingTime).isNotNull();
        service.create(form()); assertThat(bomTime()).isEqualTo(bomTime); assertThat(routingTime()).isEqualTo(routingTime);
        assertThat(mapper.findAllocation(planId).getAllocatedQty()).isEqualByComparingTo("200");
        jdbc.update("UPDATE manufacturing_process SET is_active=0, process_name=? WHERE process_id=?", "변경 공정", processId);
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(WorkOrderRuleException.class);
        service.transition(id, action("START"));
        assertThat(service.get(id).getOrder().getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(service.get(id).getSteps().get(0).getProcessNameSnapshot()).isEqualTo("저장 당시 공정");
        assertThatThrownBy(() -> service.validateCompletion(id)).isInstanceOf(WorkOrderRuleException.class);
        jdbc.update("UPDATE manufacturing_process SET is_active=1 WHERE process_id=?", processId); service.create(form());
    }
    @Test void actualDatabaseFailureRollsBackOrderAndBothFirstUseUpdates() {
        WorkOrderMapper failing = spy(mapper);
        doAnswer(call -> {
            WorkOrder row = call.getArgument(0); mapper.insert(row);
            jdbc.update("UPDATE work_order SET ordered_qty=-1 WHERE work_order_id=?", row.getWorkOrderId()); return 1;
        }).when(failing).insert(any());
        var failingService = transactional(new WorkOrderService(failing, boms, routings, items, processes, new EnvironmentActorProvider(actor)));
        Throwable failure = catchThrowable(() -> failingService.create(form()));
        assertThat(failure).isInstanceOf(DataAccessException.class);
        Throwable sql = failure; while (sql != null && !(sql instanceof java.sql.SQLException)) sql = sql.getCause();
        assertThat(sql).isInstanceOf(java.sql.SQLException.class); assertThat(((java.sql.SQLException) sql).getErrorCode()).isEqualTo(3819);
        assertThat(ownCount()).isZero(); assertThat(bomTime()).isNull(); assertThat(routingTime()).isNull();
    }
    @Test void firstUseBlocksOldBomEditAndCancellationNeverClearsUseHistory() {
        BomService bomService = transactional(new BomService(boms, items, new EnvironmentActorProvider(actor)));
        BomForm edit = BomForm.from(bomService.get(versionId)); edit.getComponents().get(0).setRequiredQty(new BigDecimal("2"));
        Long id = service.create(form()); String firstUse = bomTime();
        assertThatThrownBy(() -> bomService.update(versionId, edit)).isInstanceOf(BomRuleException.class);
        service.transition(id, action("CANCEL")); assertThat(bomTime()).isEqualTo(firstUse); assertThat(routingTime()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT required_qty FROM bom_component WHERE bom_version_id=?", BigDecimal.class, versionId)).isEqualByComparingTo("1");
    }
    @Test void concurrentOrdersForOnePlanRemainDistinctAndFirstUseIsPreserved() throws Exception {
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Long> task = () -> { if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); return service.create(form()); };
            var first = pool.submit(task); var second = pool.submit(task); start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isNotEqualTo(second.get(10, TimeUnit.SECONDS));
            assertThat(ownCount()).isEqualTo(2L); assertThat(bomTime()).isNotNull(); assertThat(routingTime()).isNotNull();
        } finally { start.countDown(); stop(pool); }
    }
    @Test void concurrentStartAndCancelHaveOnlyOneWinner() throws Exception {
        Long id = service.create(form()); var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> changeAfter(start, id, "START")); var second = pool.submit(() -> changeAfter(start, id, "CANCEL")); start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            assertThat(service.get(id).getOrder().getStatus()).isIn("IN_PROGRESS", "CANCELLED");
        } finally { start.countDown(); stop(pool); }
    }
    @Test void processLockRechecksActivationBeforeWritingFirstUse() throws Exception {
        var pool = Executors.newFixedThreadPool(2); var locked = new CountDownLatch(1); var release = new CountDownLatch(1); var started = new CountDownLatch(1);
        try {
            var holder = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                processes.findByIdForUpdate(processId); locked.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
                jdbc.update("UPDATE manufacturing_process SET is_active=0 WHERE process_id=?", processId);
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue(); var writer = pool.submit(() -> { started.countDown(); return service.create(form()); });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue(); assertThatThrownBy(() -> writer.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); holder.get(10, TimeUnit.SECONDS); assertThatThrownBy(() -> writer.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(WorkOrderRuleException.class);
            assertThat(ownCount()).isZero(); assertThat(bomTime()).isNull(); assertThat(routingTime()).isNull();
        } finally { release.countDown(); stop(pool); }
    }
    private boolean changeAfter(CountDownLatch start, Long id, String action) throws Exception { if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); try { service.transition(id, action(action)); return true; } catch (WorkOrderRuleException rejected) { return false; } }
    @SuppressWarnings("unchecked") private <T> T transactional(T target) { var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true); proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource())); return (T) proxy.getProxy(); }
    private void stop(java.util.concurrent.ExecutorService pool) throws InterruptedException { pool.shutdownNow(); if (!pool.awaitTermination(12, TimeUnit.SECONDS)) throw new IllegalStateException("Test threads did not stop."); }
    private Long ownId(String sql) { return jdbc.queryForObject(sql, Long.class, actor); }
    private Long ownCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM work_order WHERE created_by=?", Long.class, actor); }
    private String bomTime() { return jdbc.queryForObject("SELECT CAST(first_used_at AS CHAR) FROM bom_version WHERE bom_version_id=?", String.class, versionId); }
    private String routingTime() { return jdbc.queryForObject("SELECT CAST(first_used_at AS CHAR) FROM routing_revision WHERE routing_revision_id=?", String.class, revisionId); }
    private WorkOrderForm form() { var form = new WorkOrderForm(); form.setProductionPlanId(planId); form.setProductItemId(productId); form.setBomVersionId(versionId); form.setRoutingRevisionId(revisionId); form.setOrderedQty(new BigDecimal("100")); return form; }
    private WorkOrderTransitionForm action(String value) { var form = new WorkOrderTransitionForm(); form.setAction(value); return form; }
    private String env(String name) { String value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing dedicated test variable: " + name); return value; }
}
