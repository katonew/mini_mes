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
@EnabledIfEnvironmentVariable(named="MES_RETURN_MYSQL_TEST", matches="LOCAL_TEST_ONLY")
class MaterialReturnMysqlIntegrationTests {
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private MaterialConsumptionMapper mapper;
    private WorkOrderMapper orders;
    private BomMapper boms;
    private MaterialConsumptionOutcomeCodec codec;
    private MaterialConsumptionService service;
    private MaterialReturnMapper returns;
    private RawMaterialLotMapper rawLots;
    private MaterialReturnOutcomeCodec returnCodec;
    private MaterialReturnService returnService;
    private Long consumptionId;
    private java.util.Map<String,Object> originalConsumption;
    private String actor;
    private Long planId, productId, rawId, lotId, versionId, componentId, revisionId, orderId;

    @BeforeEach void setUp() throws Exception { setUp("EA"); }
    private void setUp(String materialUnit) throws Exception {
        String database = env("RETURN_TEST_DB_NAME");
        if (!database.matches("[a-z0-9_]+_return_test")) throw new IllegalArgumentException("Use a dedicated *_return_test schema.");
        int port = Integer.parseInt(System.getenv().getOrDefault("RETURN_TEST_DB_PORT", "3306"));
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid local test port.");
        var source = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:" + port + "/" + database
                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true", env("RETURN_TEST_DB_USER"), env("RETURN_TEST_DB_PASSWORD"));
        jdbc = new JdbcTemplate(source); assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(database);
        transactions = new DataSourceTransactionManager(source); transactions.setDefaultTimeout(10);
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(source); var config = new Configuration(); config.setMapUnderscoreToCamelCase(true); factory.setConfiguration(config);
        var resolver = new PathMatchingResourcePatternResolver();
        factory.setMapperLocations(resolver.getResource("classpath:mappers/lot/MaterialReturnMapper.xml"), resolver.getResource("classpath:mappers/lot/MaterialConsumptionMapper.xml"), resolver.getResource("classpath:mappers/lot/RawMaterialLotMapper.xml"), resolver.getResource("classpath:mappers/workorder/WorkOrderMapper.xml"), resolver.getResource("classpath:mappers/bom/BomMapper.xml"));
        factory.afterPropertiesSet(); var sessions = new SqlSessionTemplate(factory.getObject());
        mapper = sessions.getMapper(MaterialConsumptionMapper.class); orders = sessions.getMapper(WorkOrderMapper.class); boms = sessions.getMapper(BomMapper.class);
        codec = new MaterialConsumptionOutcomeCodec(new ObjectMapper().findAndRegisterModules()); actor = "RET_IT_" + UUID.randomUUID();
        productId = item("P", "FINISHED", "EA"); rawId = item("R", "RAW", materialUnit);
        jdbc.update("INSERT INTO raw_material_lot(item_id,lot_no,unit_code,registered_qty,available_qty,registered_at,created_by) VALUES (?,?,?,100,100,UTC_TIMESTAMP(6),?)", rawId, actor, materialUnit, actor); lotId = ownId("raw_material_lot", "raw_lot_id");
        jdbc.update("INSERT INTO production_plan(product_item_id,planned_qty,planned_date,created_by) VALUES (?,10,'2026-10-27',?)", productId, actor); planId = ownId("production_plan", "production_plan_id");
        jdbc.update("INSERT INTO bom(product_item_id,bom_code,created_by) VALUES (?,?,?)", productId, actor, actor); Long bomId = ownId("bom", "bom_id");
        jdbc.update("INSERT INTO bom_version(bom_id,version_no,base_qty,product_unit_code,first_used_at,created_by) VALUES (?,1,1,'EA',UTC_TIMESTAMP(6),?)", bomId, actor); versionId = ownId("bom_version", "bom_version_id");
        jdbc.update("INSERT INTO bom_component(bom_version_id,material_item_id,line_no,required_qty,material_unit_code,created_by) VALUES (?,?,1,1,?,?)", versionId, rawId, materialUnit, actor); componentId = ownId("bom_component", "bom_component_id");
        jdbc.update("INSERT INTO routing(product_item_id,routing_code,created_by) VALUES (?,?,?)", productId, actor, actor); Long routingId = ownId("routing", "routing_id");
        jdbc.update("INSERT INTO routing_revision(routing_id,revision_no,first_used_at,created_by) VALUES (?,1,UTC_TIMESTAMP(6),?)", routingId, actor); revisionId = ownId("routing_revision", "routing_revision_id");
        orderId = order(); service = service(mapper);
        returns=sessions.getMapper(MaterialReturnMapper.class); rawLots=sessions.getMapper(RawMaterialLotMapper.class); returnCodec=new MaterialReturnOutcomeCodec(new ObjectMapper().findAndRegisterModules()); returnService=returnService(returns);
        consumptionId=service.create(form(orderId,"60",key())).consumptionId();
        originalConsumption=jdbc.queryForMap("SELECT * FROM material_consumption WHERE consumption_id=?",consumptionId);
    }
    @AfterEach void cleanOwnFixtures() {
        if (actor == null) return;
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            for (String table : new String[]{"material_return", "material_consumption", "request_receipt", "raw_material_lot", "work_order", "bom_component", "bom_version", "bom", "routing_revision", "routing", "production_plan", "item"}) {
                jdbc.update("DELETE FROM " + table + " WHERE created_by=?", actor);
            }
        });
    }

    @Test void returnRestoresStockPreservesSourceAndBalancesBothExistingReadModels() {
        var start=jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class);
        var outcome=returnService.create(returnForm("20",key()));
        assertThat(outcome.returnedAt()).isBetween(start,jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class));
        assertThat(outcome.availableBefore()).isEqualTo("40"); assertThat(outcome.availableAfter()).isEqualTo("60");
        assertThat(returnService.get(outcome.returnId()).getOutcome()).isEqualTo(outcome);
        assertThat(returnService.list(orderId.toString(),consumptionId.toString(),actor,"자재")).hasSize(1);
        assertThat(service.get(consumptionId).getReturnedQty()).isEqualByComparingTo("20");
        var materials=service.options(orderId,componentId).getMaterials().get(0);
        assertThat(materials.getConsumedQty()).isEqualByComparingTo("60"); assertThat(materials.getReturnedQty()).isEqualByComparingTo("20"); assertThat(materials.getNetConsumedQty()).isEqualByComparingTo("40");
        var currentLot=rawLots.findById(lotId); assertThat(currentLot.getConsumedQty()).isEqualByComparingTo("60"); assertThat(currentLot.getReturnedQty()).isEqualByComparingTo("20"); assertThat(currentLot.getAvailableQty()).isEqualByComparingTo("60");
        assertLedger(1,"60");
    }
    @Test void originalAndCumulativeLimitsRejectWithoutLeavingReceiptOrStockChanges() {
        assertThatThrownBy(() -> returnService.create(returnForm("61",key()))).isInstanceOf(MaterialReturnRuleException.class); assertLedger(0,"40");
        returnService.create(returnForm("20",key()));
        assertThatThrownBy(() -> returnService.create(returnForm("41",key()))).isInstanceOf(MaterialReturnRuleException.class); assertLedger(1,"60");
        returnService.create(returnForm("40",key())); assertLedger(2,"100");
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","0.0000001","1.0000000","1000000000000","1.5"})
    void invalidQuantitiesLeaveTheLedgerUntouched(String quantity) {
        assertThatThrownBy(() -> returnService.create(returnForm(quantity,key()))).isInstanceOf(MaterialReturnRuleException.class); assertLedger(0,"40");
    }
    @ParameterizedTest @ValueSource(strings={"M","G"})
    void sixDecimalReturnsAreExactInPreservedLotUnits(String unit) throws Exception {
        cleanOwnFixtures(); setUp(unit);
        var outcome=returnService.create(returnForm("0.000001",key())); assertThat(outcome.unitCode()).isEqualTo(unit); assertLedger(1,"40.000001");
        assertThatThrownBy(() -> returnService.create(returnForm("0.0000001",key()))).isInstanceOf(MaterialReturnRuleException.class); assertLedger(1,"40.000001");
    }
    @ParameterizedTest @ValueSource(strings={"READY","COMPLETED","CANCELLED"})
    void newReturnsRejectClosedStatesButHistoricalSuccessCanReplay(String state) {
        String requestKey=key(); var original=returnService.create(returnForm("20",requestKey));
        if (state.equals("READY")) jdbc.update("UPDATE work_order SET status='READY',started_at=NULL,started_by=NULL WHERE work_order_id=?",orderId);
        else closedState(state);
        assertThatThrownBy(() -> returnService.create(returnForm("1",key()))).isInstanceOf(MaterialReturnRuleException.class);
        assertThat(returnService.get(original.returnId()).getOutcome()).isEqualTo(original);
        assertThat(returnService.create(returnForm("20.000000",requestKey))).isEqualTo(original);
        var changed=returnForm("20",requestKey); changed.setReason("다른 사유");
        assertThatThrownBy(() -> returnService.create(changed)).isInstanceOf(MaterialReturnRuleException.class); assertLedger(1,"60");
    }
    @Test void concurrentReturnsRecalculateCommittedSumAfterParentLock() throws Exception {
        returnService.create(returnForm("20",key()));
        var locked=new CountDownLatch(1); var release=new CountDownLatch(1); var started=new CountDownLatch(1); var paused=spy(returns);
        doAnswer(call -> { var row=returns.findConsumptionForUpdate(call.getArgument(0)); locked.countDown(); await(release); return row; }).when(paused).findConsumptionForUpdate(consumptionId);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(() -> returnService(paused).create(returnForm("30",key()))); assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var second=pool.submit(() -> { started.countDown(); return returnService.create(returnForm("20",key())); }); assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); first.get(12,TimeUnit.SECONDS);
            assertThatThrownBy(() -> second.get(12,TimeUnit.SECONDS)).hasCauseInstanceOf(MaterialReturnRuleException.class); assertLedger(2,"90");
        } finally { release.countDown(); stop(pool); }
    }
    @Test void concurrentSameKeyWaitsAndReplaysOneReturn() throws Exception {
        String requestKey=key(); var claimed=new CountDownLatch(1); var release=new CountDownLatch(1); var started=new CountDownLatch(1); var paused=spy(returns);
        doAnswer(call -> { int count=returns.claimReceipt(call.getArgument(0)); claimed.countDown(); await(release); return count; }).when(paused).claimReceipt(any());
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(() -> returnService(paused).create(returnForm("20",requestKey))); assertThat(claimed.await(5,TimeUnit.SECONDS)).isTrue();
            var second=pool.submit(() -> { started.countDown(); return returnService.create(returnForm("20.000000",requestKey)); }); assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); assertThat(first.get(12,TimeUnit.SECONDS)).isEqualTo(second.get(12,TimeUnit.SECONDS)); assertLedger(1,"60");
        } finally { release.countDown(); stop(pool); }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void returnAndConsumptionOnSameLotFollowOneLockOrder(boolean differentOrder) throws Exception {
        Long consumptionOrder=differentOrder ? order() : orderId;
        var locked=new CountDownLatch(1); var release=new CountDownLatch(1); var started=new CountDownLatch(1); var paused=spy(returns);
        doAnswer(call -> { var lot=returns.findLotForUpdate(call.getArgument(0)); locked.countDown(); await(release); return lot; }).when(paused).findLotForUpdate(lotId);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(() -> returnService(paused).create(returnForm("20",key()))); assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var second=pool.submit(() -> { started.countDown(); return service.create(form(consumptionOrder,"50",key())); }); assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); first.get(12,TimeUnit.SECONDS); second.get(12,TimeUnit.SECONDS); assertLedger(1,"10");
        } finally { release.countDown(); stop(pool); }
    }
    @ParameterizedTest @ValueSource(strings={"RESTORE","INSERT","FINISH","ROWS","RESULTS"})
    void failuresAfterEachWriteRollBackReceiptReturnAndInventory(String phase) {
        var failing=spy(returns); var reached=new java.util.concurrent.atomic.AtomicBoolean();
        if (phase.equals("RESTORE")) doAnswer(call -> { returns.increaseAvailable(call.getArgument(0),call.getArgument(1)); reached.set(true); throw new DataIntegrityViolationException("after restore"); }).when(failing).increaseAvailable(anyLong(),any());
        if (phase.equals("INSERT")) doAnswer(call -> { returns.insert(call.getArgument(0)); reached.set(true); throw new DataIntegrityViolationException("after insert"); }).when(failing).insert(any());
        if (phase.equals("FINISH")) doAnswer(call -> { returns.finishReceipt(call.getArgument(0),call.getArgument(1)); reached.set(true); throw new DataIntegrityViolationException("after receipt"); }).when(failing).finishReceipt(anyLong(),anyString());
        if (phase.equals("ROWS")) doAnswer(call -> { returns.insert(call.getArgument(0)); reached.set(true); return 0; }).when(failing).insert(any());
        if (phase.equals("RESULTS")) doAnswer(call -> { reached.set(true); return 2; }).when(failing).countReceiptResults(anyLong());
        assertThatThrownBy(() -> returnService(failing).create(returnForm("20",key()))).isInstanceOf(DataAccessException.class); assertThat(reached.get()).isTrue(); assertLedger(0,"40");
    }
    @Test void databaseConstraintFailureAfterRestorationRollsBackEverything() {
        var failing=spy(returns);
        doAnswer(call -> { MaterialReturn row=call.getArgument(0); row.setReturnedQty(BigDecimal.ZERO); return returns.insert(row); }).when(failing).insert(any());
        assertThatThrownBy(() -> returnService(failing).create(returnForm("20",key()))).isInstanceOf(DataAccessException.class); assertLedger(0,"40");
    }
    @Test void actualLockWaitTimeoutRollsBackTheClaimedReceipt() throws Exception {
        var locked=new CountDownLatch(1); var release=new CountDownLatch(1); var pool=Executors.newSingleThreadExecutor(); var timed=spy(returns);
        doAnswer(call -> { jdbc.execute("SET SESSION innodb_lock_wait_timeout=1"); return returns.claimReceipt(call.getArgument(0)); }).when(timed).claimReceipt(any());
        try {
            var holder=pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> { returns.findLotForUpdate(lotId); locked.countDown(); await(release); }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            Throwable failure=catchThrowable(() -> returnService(timed).create(returnForm("20",key()))); assertThat(failure).isInstanceOf(DataAccessException.class);
            Throwable cause=failure; while (cause!=null && !(cause instanceof java.sql.SQLException)) cause=cause.getCause();
            assertThat(cause).isInstanceOf(java.sql.SQLException.class); assertThat(((java.sql.SQLException)cause).getErrorCode()).isEqualTo(1205);
            release.countDown(); holder.get(12,TimeUnit.SECONDS); assertLedger(0,"40");
        } finally { release.countDown(); stop(pool); }
    }
    @Test void latestWorkOrderStateIsCheckedAfterWaitingForTheLock() throws Exception {
        var locked=new CountDownLatch(1); var release=new CountDownLatch(1); var started=new CountDownLatch(1); var pool=Executors.newFixedThreadPool(2);
        try {
            var holder=pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> { orders.findByIdForUpdate(orderId); locked.countDown(); await(release); closedState("COMPLETED"); }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var pending=pool.submit(() -> { started.countDown(); return returnService.create(returnForm("20",key())); }); assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> pending.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); holder.get(12,TimeUnit.SECONDS); assertThatThrownBy(() -> pending.get(12,TimeUnit.SECONDS)).hasCauseInstanceOf(MaterialReturnRuleException.class); assertLedger(0,"40");
        } finally { release.countDown(); stop(pool); }
    }
    private MaterialReturnService returnService(MaterialReturnMapper selected) {
        var proxy=new ProxyFactory(new MaterialReturnTransactionService(selected,orders,returnCodec)); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));
        return new MaterialReturnService(returns,(MaterialReturnTransactionService)proxy.getProxy(),() -> actor,returnCodec);
    }
    private MaterialReturnForm returnForm(String quantity,String requestKey) {
        var form=new MaterialReturnForm(); form.setConsumptionId(consumptionId); form.setReturnedQty(new BigDecimal(quantity)); form.setReason("미사용 자재 반환"); form.setRequestKey(requestKey); return form;
    }
    private void assertLedger(long count,String stock) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM material_return WHERE created_by=?",Long.class,actor)).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM request_receipt WHERE created_by=? AND operation_code='MATERIAL_RETURN'",Long.class,actor)).isEqualTo(count);
        assertThat(available()).isEqualByComparingTo(stock);
        assertThat(jdbc.queryForMap("SELECT * FROM material_consumption WHERE consumption_id=?",consumptionId)).isEqualTo(originalConsumption);
        BigDecimal ledger=jdbc.queryForObject("SELECT l.registered_qty-(SELECT COALESCE(SUM(c.consumed_qty),0) FROM material_consumption c WHERE c.raw_lot_id=l.raw_lot_id)+(SELECT COALESCE(SUM(r.returned_qty),0) FROM material_return r JOIN material_consumption c ON c.consumption_id=r.consumption_id WHERE c.raw_lot_id=l.raw_lot_id) FROM raw_material_lot l WHERE l.raw_lot_id=?",BigDecimal.class,lotId);
        assertThat(available()).isEqualByComparingTo(ledger);
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(returned_qty),0) FROM material_return WHERE consumption_id=?",BigDecimal.class,consumptionId)).isLessThanOrEqualTo(new BigDecimal("60"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM production_result WHERE created_by=?",Long.class,actor)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM finished_goods_lot WHERE created_by=?",Long.class,actor)).isZero();
    }
    private MaterialConsumptionService service(MaterialConsumptionMapper selected) {
        var target = new MaterialConsumptionTransactionService(selected, orders, boms, codec); var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return new MaterialConsumptionService(mapper, (MaterialConsumptionTransactionService) proxy.getProxy(), new EnvironmentActorProvider(actor), codec);
    }
    private Long item(String suffix, String kind, String unit) {
        jdbc.update("INSERT INTO item(item_code,item_name,item_kind,unit_code,is_active,created_by) VALUES (?,?,?,?,1,?)", actor+suffix, "반환 테스트 자재", kind, unit, actor);
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
    private BigDecimal available() { return jdbc.queryForObject("SELECT available_qty FROM raw_material_lot WHERE raw_lot_id=?", BigDecimal.class, lotId); }
    private Long ownId(String table, String column) { return jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE created_by=?", Long.class, actor); }
    private String key() { return UUID.randomUUID().toString(); }
    private String env(String name) { String value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing dedicated test variable: " + name); return value; }
    private void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test latch timeout."); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }
    private void stop(java.util.concurrent.ExecutorService pool) throws InterruptedException { pool.shutdownNow(); if (!pool.awaitTermination(12, TimeUnit.SECONDS)) throw new IllegalStateException("Test threads did not stop."); }
}
