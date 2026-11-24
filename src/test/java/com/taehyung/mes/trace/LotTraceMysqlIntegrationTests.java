package com.taehyung.mes.trace;

import com.taehyung.mes.bom.BomMapper;
import com.taehyung.mes.routing.RoutingMapper;
import com.taehyung.mes.lot.*;
import com.taehyung.mes.production.*;
import com.taehyung.mes.quality.*;
import com.taehyung.mes.workorder.*;
import java.util.List;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** 기존 업무 DB에 연결하지 않는다. 별도 MySQL 인스턴스의 합성 데이터만 사용한다. */
@EnabledIfEnvironmentVariable(named="MES_TRACE_MYSQL_TEST", matches="ISOLATED_TEST_ONLY")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LotTraceMysqlIntegrationTests {
    private LotTraceService service;
    private LotTraceMapper mapper;
    private JdbcTemplate jdbc;
    @BeforeAll void setUp() throws Exception {
        var source = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13317/mini_mes_trace_test"
                + "?characterEncoding=UTF-8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true", "root", "");
        jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("mini_mes_trace_test");
        assertThat(jdbc.queryForObject("SELECT @@port", Integer.class)).isEqualTo(13317);
        var transactions = new DataSourceTransactionManager(source);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM item", Long.class) == 0) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> seed());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM item WHERE created_by='TRACE_TEST_ONLY'", Long.class)).isEqualTo(3L);
        var factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
        var config = new Configuration(); config.setMapUnderscoreToCamelCase(true); factory.setConfiguration(config);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mappers/**/*.xml"));
        factory.afterPropertiesSet(); var session = new SqlSessionTemplate(factory.getObject());
        mapper = session.getMapper(LotTraceMapper.class);
        var target = new LotTraceService(session.getMapper(ProductionResultMapper.class), session.getMapper(WorkOrderMapper.class),
                session.getMapper(MaterialConsumptionMapper.class), session.getMapper(MaterialReturnMapper.class),
                session.getMapper(QualityInspectionMapper.class), session.getMapper(BomMapper.class), session.getMapper(RoutingMapper.class),
                session.getMapper(RawMaterialLotMapper.class), mapper);
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        service = (LotTraceService) proxy.getProxy();
    }
    private void seed() {
        String sql = """
            INSERT INTO item(item_id,item_code,item_name,item_kind,unit_code,created_by) VALUES
              (1,'TRACE-P','추적 검증 제품','FINISHED','EA','TRACE_TEST_ONLY'),
              (2,'TRACE-M1','추적 검증 원자재 A','RAW','M','TRACE_TEST_ONLY'),
              (3,'TRACE-M2','추적 검증 원자재 B','RAW','M','TRACE_TEST_ONLY');
            INSERT INTO bom(bom_id,product_item_id,bom_code,created_by) VALUES(1,1,'TRACE-BOM','TRACE_TEST_ONLY');
            INSERT INTO bom_version(bom_version_id,bom_id,version_no,base_qty,product_unit_code,created_by) VALUES(1,1,1,10,'EA','TRACE_TEST_ONLY');
            INSERT INTO bom_component(bom_component_id,bom_version_id,material_item_id,line_no,required_qty,material_unit_code,created_by) VALUES
              (1,1,2,1,10,'M','TRACE_TEST_ONLY'),(2,1,3,2,5,'M','TRACE_TEST_ONLY');
            INSERT INTO manufacturing_process(process_id,process_code,process_name,created_by) VALUES(1,'TRACE-PR','추적 검증 공정','TRACE_TEST_ONLY');
            INSERT INTO routing(routing_id,product_item_id,routing_code,created_by) VALUES(1,1,'TRACE-RT','TRACE_TEST_ONLY');
            INSERT INTO routing_revision(routing_revision_id,routing_id,revision_no,created_by) VALUES(1,1,1,'TRACE_TEST_ONLY');
            INSERT INTO routing_step(routing_revision_id,process_id,step_no,process_name_snapshot,created_by) VALUES(1,1,1,'추적 검증 공정','TRACE_TEST_ONLY');
            INSERT INTO production_plan(production_plan_id,product_item_id,planned_qty,planned_date,created_by) VALUES(1,1,40,'2026-11-24','TRACE_TEST_ONLY');
            INSERT INTO work_order(work_order_id,production_plan_id,product_item_id,bom_version_id,routing_revision_id,ordered_qty,product_unit_code,status,inspection_target_qty,started_at,started_by,completed_at,completed_by,created_by) VALUES
              (1,1,1,1,1,10,'EA','COMPLETED',8,'2026-11-24 01:00:00','TRACE_TEST_ONLY','2026-11-24 02:00:00','TRACE_TEST_ONLY','TRACE_TEST_ONLY'),
              (2,1,1,1,1,10,'EA','IN_PROGRESS',NULL,'2026-11-24 01:00:00','TRACE_TEST_ONLY',NULL,NULL,'TRACE_TEST_ONLY'),
              (3,1,1,1,1,10,'EA','IN_PROGRESS',NULL,'2026-11-24 01:00:00','TRACE_TEST_ONLY',NULL,NULL,'TRACE_TEST_ONLY');
            INSERT INTO raw_material_lot(raw_lot_id,item_id,lot_no,unit_code,registered_qty,available_qty,registered_at,created_by) VALUES
              (1,2,'RM-TRACE-A','M',100,77,'2026-11-24 00:00:00','TRACE_TEST_ONLY'),
              (2,3,'RM-TRACE-B','M',100,96,'2026-11-24 00:00:00','TRACE_TEST_ONLY'),
              (3,2,'RM-TRACE-UNUSED','M',100,100,'2026-11-24 00:00:00','TRACE_TEST_ONLY');
            """;
        for (String statement : sql.split(";")) { if (!statement.isBlank()) { jdbc.execute(statement); } }
        for (int n=1; n<=15; n++) {
            String operation = n<=5 ? "MATERIAL_CONSUME" : n<=10 ? "MATERIAL_RETURN" : n<=13 ? "PRODUCTION_RESULT" : "QUALITY_INSPECTION";
            jdbc.update("INSERT INTO request_receipt(receipt_id,scope_key,operation_code,request_key,payload_fingerprint,initial_outcome_snapshot,processed_at,created_by) VALUES (?,'TRACE_TEST_ONLY',?,?,UNHEX(SHA2(?,256)),'{}',UTC_TIMESTAMP(6),'TRACE_TEST_ONLY')", n,operation,"trace-"+n,"trace-"+n);
        }
        // 한 투입의 복수 반환과 다른 LOT·생산·검사의 1:N 조합을 함께 검증한다.
        jdbc.execute("INSERT INTO material_consumption(consumption_id,work_order_id,raw_lot_id,bom_component_id,receipt_id,consumed_qty,consumed_at,created_by) VALUES (1,1,1,1,1,10,UTC_TIMESTAMP(6),'TRACE_TEST_ONLY'),(2,1,1,1,2,20,UTC_TIMESTAMP(6),'TRACE_TEST_ONLY'),(3,2,1,1,3,7,UTC_TIMESTAMP(6),'TRACE_TEST_ONLY'),(4,1,2,2,4,5,UTC_TIMESTAMP(6),'TRACE_TEST_ONLY'),(5,3,1,1,5,2,UTC_TIMESTAMP(6),'TRACE_TEST_ONLY')");
        jdbc.execute("INSERT INTO material_return(consumption_id,receipt_id,returned_qty,returned_at,reason,created_by) VALUES (1,6,3,UTC_TIMESTAMP(6),'검증 반환','TRACE_TEST_ONLY'),(1,7,2,UTC_TIMESTAMP(6),'검증 반환','TRACE_TEST_ONLY'),(2,8,4,UTC_TIMESTAMP(6),'검증 반환','TRACE_TEST_ONLY'),(3,9,7,UTC_TIMESTAMP(6),'전량 반환','TRACE_TEST_ONLY'),(4,10,1,UTC_TIMESTAMP(6),'검증 반환','TRACE_TEST_ONLY')");
        jdbc.execute("INSERT INTO finished_goods_lot(finished_lot_id,work_order_id,lot_no,created_by) VALUES(1,1,'FG-TRACE-A','TRACE_TEST_ONLY'),(2,3,'FG-TRACE-PARTIAL','TRACE_TEST_ONLY')");
        jdbc.execute("INSERT INTO production_result(production_result_id,finished_lot_id,receipt_id,produced_qty,good_qty,defect_qty,produced_at,worker_ref,revision_no,created_by) VALUES(1,1,11,4,3,1,UTC_TIMESTAMP(6),'검증 작업자',2,'TRACE_TEST_ONLY'),(2,1,12,6,5,1,UTC_TIMESTAMP(6),'검증 작업자',1,'TRACE_TEST_ONLY'),(3,2,13,4,3,1,UTC_TIMESTAMP(6),'검증 작업자',1,'TRACE_TEST_ONLY')");
        jdbc.execute("INSERT INTO production_result_correction(production_result_id,from_revision,to_revision,before_produced_qty,after_produced_qty,before_good_qty,after_good_qty,before_defect_qty,after_defect_qty,before_produced_at,after_produced_at,before_worker_ref,after_worker_ref,reason,created_by) VALUES(1,1,2,3,4,2,3,1,1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'검증 작업자','검증 작업자','정정 반영 검증','TRACE_TEST_ONLY')");
        jdbc.execute("INSERT INTO quality_inspection(finished_lot_id,receipt_id,round_no,inspected_qty,accepted_qty,rejected_qty,inspected_at,inspector_ref,created_by) VALUES(1,14,1,3,2,1,UTC_TIMESTAMP(6),'검증 검사자','TRACE_TEST_ONLY'),(1,15,2,4,3,1,UTC_TIMESTAMP(6),'검증 검사자','TRACE_TEST_ONLY')");
    }
    @Test void sameRawLotReachesThreeOrdersWithoutMultiplyingQuantities() {
        var trace = service.forward(1L); assertThat(trace.getOrders()).hasSize(3);
        var row = order(trace,1L);
        assertThat(row.getConsumedQty()).isEqualByComparingTo("30");
        assertThat(row.getReturnedQty()).isEqualByComparingTo("9");
        assertThat(row.getNetConsumedQty()).isEqualByComparingTo("21");
        assertThat(row.getLot().getProducedQty()).isEqualByComparingTo("10");
        assertThat(row.getLot().getGoodQty()).isEqualByComparingTo("8");
        assertThat(row.getLot().getDefectQty()).isEqualByComparingTo("2");
        assertThat(row.getLot().getResultCount()).isEqualTo(2L);
    }
    @Test void fullyReturnedOrderWithoutFinishedLotRemainsVisible() {
        var row = order(service.forward(1L),2L);
        assertThat(row.getConsumedQty()).isEqualByComparingTo("7");
        assertThat(row.getReturnedQty()).isEqualByComparingTo("7");
        assertThat(row.isFullyReturned()).isTrue(); assertThat(row.getLot()).isNull();
        assertThat(row.getOrder().getInspectionTargetQty()).isNull(); assertThat(row.getQuality()).isNull();
    }
    @Test void forwardAndBackwardAgreeForEveryMaterialAndCurrentProductionAndInspection() {
        var back = service.get(1L); assertThat(back.getMaterialLots()).hasSize(2);
        for (var material : back.getMaterialLots()) {
            var forward = order(service.forward(material.getSource().getRawLotId()),1L);
            assertThat(forward.getConsumedQty()).isEqualByComparingTo(material.getConsumedQty());
            assertThat(forward.getReturnedQty()).isEqualByComparingTo(material.getReturnedQty());
            assertThat(forward.getNetConsumedQty()).isEqualByComparingTo(material.getNetConsumedQty());
            assertThat(forward.getLot().getProducedQty()).isEqualByComparingTo(back.getTotals().getProducedQty());
            assertThat(forward.getLot().getGoodQty()).isEqualByComparingTo(back.getTotals().getGoodQty());
            assertThat(forward.getLot().getDefectQty()).isEqualByComparingTo(back.getTotals().getDefectQty());
            assertThat(forward.getQuality().getInspectedQty()).isEqualByComparingTo(back.getQuality().getInspectedQty());
            assertThat(forward.getQuality().getAcceptedQty()).isEqualByComparingTo("5");
            assertThat(forward.getQuality().getRejectedQty()).isEqualByComparingTo("2");
            assertThat(forward.getQuality().getUninspectedQty()).isEqualByComparingTo("1");
        }
        assertThat(back.getWarnings()).isEmpty();
    }
    @Test void inProgressLotKeepsInspectionTargetUnconfirmedAndNoInspections() {
        var row = order(service.forward(1L),3L);
        assertThat(row.getLot().getProducedQty()).isEqualByComparingTo("4");
        assertThat(row.getQuality().getInspectionTargetQty()).isNull();
        assertThat(row.getQuality().getInspectedQty()).isZero();
        assertThat(row.getInspectionProgressLabel()).isEqualTo("완료 시 확정 · 검사 미실시");
        assertThat(service.get(2L).getQuality().getUninspectedQty()).isNull();
    }
    @Test void searchesUnusedAndMissingLotsAreDistinct() {
        assertThat(service.searchRaw("RM-TRACE")).hasSize(3);
        assertThat(service.searchFinished("FG-TRACE")).hasSize(2);
        assertThat(service.searchRaw("NO-SUCH-LOT")).isEmpty();
        assertThat(service.searchFinished("NO-SUCH-LOT")).isEmpty();
        assertThat(service.forward(3L).getOrders()).isEmpty();
        assertThat(mapper.findForward(999L)).isEmpty();
        assertThatThrownBy(() -> service.forward(999L)).isInstanceOf(RawMaterialLotNotFoundException.class);
        assertThatThrownBy(() -> service.get(999L)).isInstanceOf(ProductionResultNotFoundException.class);
    }
    private ForwardLotTraceOrder order(ForwardLotTraceDetails trace, Long id) {
        return trace.getOrders().stream().filter(row -> id.equals(row.getOrder().getWorkOrderId())).findFirst().orElseThrow();
    }
}
