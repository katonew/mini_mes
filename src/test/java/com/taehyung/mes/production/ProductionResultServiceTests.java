package com.taehyung.mes.production;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taehyung.mes.common.actor.EnvironmentActorProvider;
import com.taehyung.mes.workorder.WorkOrder;
import com.taehyung.mes.workorder.WorkOrderMapper;
import com.taehyung.mes.workorder.WorkOrderTotals;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductionResultServiceTests {
    private ProductionResultMapper mapper;
    private WorkOrderMapper orders;
    private ProductionResultService service;
    private ProductionResultOutcomeCodec codec;
    private WorkOrder order;
    private WorkOrderTotals totals;
    private final AtomicReference<ProductionResult> stored = new AtomicReference<>();
    private FinishedGoodsLot lot;
    @BeforeEach void setUp() {
        mapper = mock(ProductionResultMapper.class); orders = mock(WorkOrderMapper.class);
        codec = new ProductionResultOutcomeCodec(new ObjectMapper().findAndRegisterModules());
        var writer = new ProductionResultTransactionService(mapper, orders, codec);
        service = new ProductionResultService(mapper, writer, codec, orders, new EnvironmentActorProvider("DEV"));
        order = new WorkOrder(); order.setWorkOrderId(1L); order.setStatus("IN_PROGRESS"); order.setProductUnitCode("EA"); order.setOrderedQty(new BigDecimal("10"));
        totals = new WorkOrderTotals(); totals.setResultCount(0L); totals.setProducedQty(BigDecimal.ZERO);
        totals.setGoodQty(BigDecimal.ZERO); totals.setDefectQty(BigDecimal.ZERO);
        lot = new FinishedGoodsLot(); lot.setWorkOrderId(1L); lot.setLotNo("FG-1");
        when(orders.findByIdForUpdate(1L)).thenReturn(order); when(orders.findTotals(1L)).thenReturn(totals);
        when(mapper.claimReceipt(any())).thenAnswer(call -> { ((ProductionResultReceipt) call.getArgument(0)).setReceiptId(11L); return 1; });
        when(mapper.insertLot(any())).thenAnswer(call -> { ((FinishedGoodsLot) call.getArgument(0)).setFinishedLotId(20L); return 1; });
        when(mapper.insert(any())).thenAnswer(call -> {
            var row = (ProductionResult) call.getArgument(0); row.setProductionResultId(30L); row.setWorkOrderId(1L);
            row.setLotNo("FG-1"); row.setProducedAt(LocalDateTime.of(2026,10,28,1,2,3,123456000)); row.setRevisionNo(1L);
            stored.set(row); return 1;
        });
        when(mapper.findByReceipt(11L)).thenAnswer(call -> stored.get());
        when(mapper.finishReceipt(eq(11L), anyString())).thenReturn(1); when(mapper.countReceiptResults(11L)).thenReturn(1);
    }
    private ProductionResultForm form(String lotNo, String produced, String good, String defect) {
        var form = new ProductionResultForm(); form.setWorkOrderId(1L); form.setLotNo(lotNo);
        form.setProducedQty(new BigDecimal(produced)); form.setGoodQty(new BigDecimal(good));
        form.setDefectQty(new BigDecimal(defect)); form.setWorkerRef("WORKER"); form.setRequestKey(UUID.randomUUID().toString()); return form;
    }
    @Test void firstResultCreatesOneLotAndFinishesOneReceiptAfterStoring() {
        var result = service.create(form("FG-1", "4.000000", "3", "1"));
        assertThat(result.producedQty()).isEqualTo("4"); assertThat(result.lotNo()).isEqualTo("FG-1");
        var sequence = inOrder(mapper, orders);
        sequence.verify(mapper).claimReceipt(any()); sequence.verify(orders).findByIdForUpdate(1L);
        sequence.verify(orders).findTotals(1L); sequence.verify(mapper).findLotByWorkOrder(1L);
        sequence.verify(mapper).insertLot(any()); sequence.verify(mapper).insert(any());
        sequence.verify(mapper).finishReceipt(eq(11L), anyString());
    }
    @Test void laterResultUsesExistingLotAndRejectsAnotherNumber() {
        lot.setFinishedLotId(20L); when(mapper.findLotByWorkOrder(1L)).thenReturn(lot);
        totals.setResultCount(1L); totals.setProducedQty(new BigDecimal("4")); totals.setGoodQty(new BigDecimal("3")); totals.setDefectQty(BigDecimal.ONE);
        var result = service.create(form(null, "6", "5", "1"));
        assertThat(result.finishedLotId()).isEqualTo(20L); verify(mapper, never()).insertLot(any());
        assertThatThrownBy(() -> service.create(form("FG-2", "1", "1", "0"))).isInstanceOf(ProductionResultRuleException.class);
    }
    @Test void stateAndCumulativeLimitRejectBeforeAnyLotWrite() {
        totals.setProducedQty(new BigDecimal("9"));
        assertThatThrownBy(() -> service.create(form("FG-1", "2", "2", "0"))).isInstanceOf(ProductionResultRuleException.class);
        order.setStatus("COMPLETED");
        assertThatThrownBy(() -> service.create(form("FG-1", "1", "1", "0"))).isInstanceOf(ProductionResultRuleException.class);
        verify(mapper, never()).insertLot(any()); verify(mapper, never()).insert(any());
    }
    @Test void sameUuidReplaysOriginalSnapshotAndDifferentPayloadFails() {
        var input = form("FG-1", "4", "3", "1"); var original = service.create(input);
        var receipt = new ProductionResultReceipt(); receipt.setReceiptId(11L); receipt.setOperationCode("PRODUCTION_RESULT"); receipt.setCreatedBy("DEV");
        receipt.setProcessedAt(LocalDateTime.now()); receipt.setSnapshot(codec.encode(original));
        receipt.setPayloadFingerprint(ProductionResultCommand.from(input, "DEV").fingerprint());
        when(mapper.claimReceipt(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(mapper.findReceipt(anyString(), eq(input.getRequestKey()))).thenReturn(receipt);
        order.setStatus("COMPLETED");
        assertThat(service.create(input)).isEqualTo(original);
        input.setGoodQty(new BigDecimal("2")); input.setDefectQty(new BigDecimal("2"));
        assertThatThrownBy(() -> service.create(input)).isInstanceOf(ProductionResultRuleException.class);
        verify(mapper, times(1)).insertLot(any()); verify(mapper, times(1)).insert(any());
    }
}
