package com.taehyung.mes.trace;

import com.taehyung.mes.bom.BomMapper;
import com.taehyung.mes.routing.RoutingMapper;
import com.taehyung.mes.lot.*;
import com.taehyung.mes.production.*;
import com.taehyung.mes.quality.*;
import com.taehyung.mes.workorder.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LotTraceServiceTests {
    private RawMaterialLotMapper raw;
    private LotTraceMapper mapper;
    private ProductionResultMapper production;
    private LotTraceService service;
    @BeforeEach void setUp() {
        raw = mock(RawMaterialLotMapper.class); mapper = mock(LotTraceMapper.class);
        production = mock(ProductionResultMapper.class);
        service = new LotTraceService(production, mock(WorkOrderMapper.class), mock(MaterialConsumptionMapper.class),
                mock(MaterialReturnMapper.class), mock(QualityInspectionMapper.class), mock(BomMapper.class),
                mock(RoutingMapper.class), raw, mapper);
    }
    @Test void forwardKeepsLotWithoutConsumptionAsNormalEmptyState() {
        var lot = new RawMaterialLot(); when(raw.findById(1L)).thenReturn(lot);
        when(mapper.findForward(1L)).thenReturn(List.of());
        var trace = service.forward(1L);
        assertThat(trace.getLot()).isSameAs(lot); assertThat(trace.getOrders()).isEmpty();
    }
    @Test void missingLotAndInvalidIdsNeverReturnFabricatedData() {
        assertThatThrownBy(() -> service.forward(null)).isInstanceOf(RawMaterialLotNotFoundException.class);
        assertThatThrownBy(() -> service.forward(0L)).isInstanceOf(RawMaterialLotNotFoundException.class);
        assertThatThrownBy(() -> service.forward(99L)).isInstanceOf(RawMaterialLotNotFoundException.class);
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ProductionResultNotFoundException.class);
        verifyNoInteractions(mapper);
    }
    @Test void searchReusesExistingReadQueriesWithoutChangingSearchText() {
        service.searchRaw(" RM-"); service.searchFinished("FG- ");
        verify(raw).findAll(" RM-", "", ""); verify(production).findLots(null, "FG- ", "");
    }
    @Test void fullyReturnedRelationRemainsAndTargetNullIsPreserved() {
        var order = new WorkOrder(); var row = new ForwardLotTraceOrder(); row.setOrder(order);
        row.setConsumedQty(new BigDecimal("10.500000")); row.setReturnedQty(new BigDecimal("10.500000"));
        assertThat(row.isFullyReturned()).isTrue(); assertThat(row.getNetConsumedQty()).isZero();
        assertThat(order.getInspectionTargetQty()).isNull();
        assertThat(row.getInspectionProgressLabel()).isEqualTo("완료 시 확정 · 검사 미실시");
        order.setInspectionTargetQty(BigDecimal.ZERO);
        assertThat(row.getInspectionProgressLabel()).isEqualTo("검사 대상 0");
    }
    @Test void inspectionProgressDescribesCompletionWithoutLotJudgement() {
        var order = new WorkOrder(); order.setInspectionTargetQty(BigDecimal.TEN);
        var row = new ForwardLotTraceOrder(); row.setOrder(order);
        var quality = new QualityInspectionLot(); quality.setInspectionTargetQty(BigDecimal.TEN);
        quality.setAcceptedQty(new BigDecimal("4")); quality.setRejectedQty(BigDecimal.ONE); quality.setLastRoundNo(1L);
        row.setQuality(quality); assertThat(row.getInspectionProgressLabel()).isEqualTo("일부 검사");
        quality.setAcceptedQty(new BigDecimal("9"));
        assertThat(row.getInspectionProgressLabel()).isEqualTo("검사 완료");
    }
}
