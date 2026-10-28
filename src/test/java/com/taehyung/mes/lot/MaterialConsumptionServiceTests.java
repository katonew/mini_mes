package com.taehyung.mes.lot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taehyung.mes.bom.*;
import com.taehyung.mes.common.actor.EnvironmentActorProvider;
import com.taehyung.mes.workorder.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MaterialConsumptionServiceTests {
    private MaterialConsumptionMapper mapper;
    private WorkOrderMapper orders;
    private BomMapper boms;
    private WorkOrder order;
    private BomComponent component;
    private RawMaterialLot lot;
    private MaterialConsumptionOutcomeCodec codec;
    private MaterialConsumptionTransactionService writer;
    private MaterialConsumptionService service;
    private final AtomicReference<MaterialConsumption> stored = new AtomicReference<>();

    @BeforeEach void setUp() {
        mapper = mock(MaterialConsumptionMapper.class); orders = mock(WorkOrderMapper.class); boms = mock(BomMapper.class);
        codec = new MaterialConsumptionOutcomeCodec(new ObjectMapper().findAndRegisterModules());
        writer = new MaterialConsumptionTransactionService(mapper, orders, boms, codec); service = service(writer);
        order = new WorkOrder(); order.setWorkOrderId(1L); order.setStatus("IN_PROGRESS"); order.setBomVersionId(10L); order.setProductItemId(20L); order.setProductUnitCode("EA"); order.setOrderedQty(BigDecimal.ONE);
        var version = new BomVersion(); version.setBomVersionId(10L); version.setProductItemId(20L); version.setProductUnitCode("EA"); version.setBaseQty(BigDecimal.ONE);
        component = new BomComponent(); component.setBomComponentId(2L); component.setBomVersionId(10L); component.setMaterialItemId(30L); component.setMaterialUnitCode("EA"); component.setRequiredQty(BigDecimal.ONE);
        lot = new RawMaterialLot(); lot.setRawLotId(3L); lot.setItemId(30L); lot.setUnitCode("EA"); lot.setAvailableQty(new BigDecimal("10"));
        when(mapper.claimReceipt(any())).thenAnswer(call -> { ((MaterialConsumptionReceipt) call.getArgument(0)).setReceiptId(11L); return 1; });
        when(orders.findByIdForUpdate(1L)).thenReturn(order); when(boms.findVersion(10L)).thenReturn(version); when(boms.findComponents(10L)).thenReturn(List.of(component));
        when(mapper.findLotForUpdate(3L)).thenReturn(lot); when(mapper.decreaseAvailable(eq(3L), any())).thenReturn(1);
        when(mapper.insert(any())).thenAnswer(call -> {
            MaterialConsumption row = call.getArgument(0); row.setConsumptionId(7L); row.setUnitCode(lot.getUnitCode()); row.setConsumedAt(LocalDateTime.of(2026,10,27,1,2,3,123456000)); stored.set(row); return 1;
        });
        when(mapper.findByReceipt(11L)).thenAnswer(call -> stored.get()); when(mapper.finishReceipt(eq(11L), anyString())).thenReturn(1); when(mapper.countReceiptResults(11L)).thenReturn(1);
    }
    @Test void consumesBeyondBomExpectedQuantityAndStoresOnlyTheNormalizedCommand() {
        var result = service.create(MaterialConsumptionContractTests.form("10.000000"));
        assertThat(result.availableBefore()).isEqualTo("10"); assertThat(result.availableAfter()).isEqualTo("0");
        assertThat(result.consumedQty()).isEqualTo("10"); assertThat(stored.get().getCreatedBy()).isEqualTo("DEV");
        var sequence = inOrder(mapper, orders, boms);
        sequence.verify(mapper).claimReceipt(any()); sequence.verify(orders).findByIdForUpdate(1L);
        sequence.verify(boms).findVersion(10L); sequence.verify(boms).findComponents(10L);
        sequence.verify(mapper).findLotForUpdate(3L); sequence.verify(mapper).decreaseAvailable(3L, new BigDecimal("10"));
        sequence.verify(mapper).insert(any()); sequence.verify(mapper).findByReceipt(11L); sequence.verify(mapper).finishReceipt(eq(11L), anyString());
    }
    @Test void rejectsEveryNonRunningStateAndMissingOrder() {
        for (String state : new String[]{"READY", "COMPLETED", "CANCELLED"}) { order.setStatus(state); rejected("1"); }
        when(orders.findByIdForUpdate(1L)).thenReturn(null); rejected("1"); verify(mapper, never()).decreaseAvailable(anyLong(), any());
    }
    @Test void fixedBomMembershipLotItemAndPreservedUnitMustMatch() {
        when(boms.findComponents(10L)).thenReturn(List.of()); rejected("1");
        when(boms.findComponents(10L)).thenReturn(List.of(component)); component.setBomVersionId(99L); rejected("1");
        component.setBomVersionId(10L); lot.setItemId(99L); rejected("1");
        lot.setItemId(30L); lot.setUnitCode("M"); rejected("1");
        when(mapper.findLotForUpdate(3L)).thenReturn(null); rejected("1"); verify(mapper, never()).decreaseAvailable(anyLong(), any());
    }
    @Test void quantityAndUnitValidationDoNotRoundOrConvert() {
        rejected("0"); rejected("-1"); rejected("1.5"); rejected("1.0000001"); rejected("1000000000000");
        for (String unit : new String[]{"M", "G"}) {
            component.setMaterialUnitCode(unit); lot.setUnitCode(unit);
            rejected("0.0000001"); rejected("1.0000000");
            assertThat(service.create(MaterialConsumptionContractTests.form("0.000001")).consumedQty()).isEqualTo("0.000001");
        }
    }
    @Test void insufficientStockAndConditionalUpdateFailureNeverCreateAConsumption() {
        rejected("11"); when(mapper.decreaseAvailable(eq(3L), any())).thenReturn(0); rejected("1"); verify(mapper, never()).insert(any());
    }
    @Test void successfulReplayUsesOriginalSnapshotAfterWorkOrderAndInventoryChanged() {
        var original = existing(); order.setStatus("COMPLETED"); lot.setAvailableQty(BigDecimal.ZERO);
        doThrow(new DuplicateKeyException("uq_request_key")).when(mapper).claimReceipt(any());
        clearInvocations(orders, boms, mapper);
        assertThat(service.create(MaterialConsumptionContractTests.form("1.000000"))).isEqualTo(original);
        verifyNoInteractions(orders, boms); verify(mapper, never()).decreaseAvailable(anyLong(), any()); verify(mapper, never()).insert(any());
        assertThatThrownBy(() -> service.create(MaterialConsumptionContractTests.form("2"))).isInstanceOf(MaterialConsumptionRuleException.class).hasMessageContaining("다른");
    }
    @Test void missingOrIncompleteSuccessReceiptDoesNotBecomeAnInventedSuccess() {
        doThrow(new DuplicateKeyException("uq_request_key")).when(mapper).claimReceipt(any());
        assertThatThrownBy(() -> service.create(MaterialConsumptionContractTests.form("1"))).isInstanceOf(DataIntegrityViolationException.class);
        verify(mapper, never()).decreaseAvailable(anyLong(), any());
    }
    @Test void realSpringProxyEndsDuplicateAttemptBeforeOpeningTheReplayTransaction() {
        var original = existing(); doThrow(new DuplicateKeyException("uq_request_key")).when(mapper).claimReceipt(any());
        var manager = mock(PlatformTransactionManager.class); var failed = new SimpleTransactionStatus(); var read = new SimpleTransactionStatus();
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(failed, read);
        var proxy = new ProxyFactory(writer); proxy.setProxyTargetClass(true); proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        assertThat(service((MaterialConsumptionTransactionService) proxy.getProxy()).create(MaterialConsumptionContractTests.form("1"))).isEqualTo(original);
        var sequence = inOrder(manager); sequence.verify(manager).getTransaction(any()); sequence.verify(manager).rollback(failed);
        sequence.verify(manager).getTransaction(any()); sequence.verify(manager).commit(read);
        var definitions = org.mockito.ArgumentCaptor.forClass(TransactionDefinition.class); verify(manager, times(2)).getTransaction(definitions.capture());
        assertThat(definitions.getAllValues()).allMatch(def -> def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Test void receiptFinishFailureAndLockFailuresRollBackThroughTheProxy() {
        for (RuntimeException failure : List.of(new DataIntegrityViolationException("finish failure"), new CannotAcquireLockException("deadlock or lock wait"))) {
            var manager = mock(PlatformTransactionManager.class); var status = new SimpleTransactionStatus(); when(manager.getTransaction(any())).thenReturn(status);
            var proxy = new ProxyFactory(writer); proxy.setProxyTargetClass(true); proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
            if (failure instanceof CannotAcquireLockException) { doThrow(failure).when(mapper).findLotForUpdate(3L); }
            else { doThrow(failure).when(mapper).finishReceipt(eq(11L), anyString()); }
            assertThatThrownBy(() -> service((MaterialConsumptionTransactionService) proxy.getProxy()).create(MaterialConsumptionContractTests.form("1"))).isSameAs(failure);
            verify(manager).rollback(status); verify(manager, never()).commit(any());
        }
    }
    @Test void searchAndDetailsUseReadModelsAndUnknownIdsReturn404Exception() {
        when(mapper.findAll(1L, "LOT", "자재")).thenReturn(List.of(new MaterialConsumption()));
        assertThat(service.list("1", "LOT", "자재")).hasSize(1);
        assertThatThrownBy(() -> service.list("bad", "", "")).isInstanceOf(MaterialConsumptionRuleException.class);
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(MaterialConsumptionNotFoundException.class);
        assertThatThrownBy(() -> service.get(0L)).isInstanceOf(MaterialConsumptionNotFoundException.class);
    }
    @Test void changingOrderKeepsItsBomChoicesAvailableWhileTheOldSelectionIsReportedMissing() {
        var material = new MaterialConsumptionMaterial(); material.setBomComponentId(2L); material.setBaseQty(BigDecimal.ONE); material.setMaterialRequiredQty(BigDecimal.ONE);
        when(mapper.findWorkOrder(1L)).thenReturn(order); when(mapper.findMaterials(1L)).thenReturn(List.of(material));
        var options = service.options(1L, 99L);
        assertThat(options.getMaterials()).containsExactly(material); assertThat(options.getLots()).isEmpty(); verify(mapper, never()).findLots(anyLong(), anyLong());
    }
    private MaterialConsumptionOutcome existing() {
        var result = service.create(MaterialConsumptionContractTests.form("1")); var command = MaterialConsumptionCommand.from(MaterialConsumptionContractTests.form("1"), "DEV");
        var receipt = new MaterialConsumptionReceipt(); receipt.setReceiptId(11L); receipt.setCreatedBy("DEV"); receipt.setOperationCode("MATERIAL_CONSUME");
        receipt.setProcessedAt(LocalDateTime.now()); receipt.setPayloadFingerprint(command.fingerprint()); receipt.setSnapshot(codec.encode(result));
        when(mapper.findReceipt(command.scopeKey(), command.requestKey())).thenReturn(receipt); return result;
    }
    private void rejected(String quantity) { assertThatThrownBy(() -> service.create(MaterialConsumptionContractTests.form(quantity))).isInstanceOf(MaterialConsumptionRuleException.class); }
    private MaterialConsumptionService service(MaterialConsumptionTransactionService selected) { return new MaterialConsumptionService(mapper, selected, new EnvironmentActorProvider("DEV"), codec); }
}
