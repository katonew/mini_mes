package com.taehyung.mes.lot;

import com.fasterxml.jackson.databind.ObjectMapper;
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

class MaterialReturnServiceTests {
    private MaterialReturnMapper mapper;
    private WorkOrderMapper orders;
    private WorkOrder order;
    private MaterialConsumption source;
    private RawMaterialLot lot;
    private MaterialReturnOutcomeCodec codec;
    private MaterialReturnTransactionService writer;
    private MaterialReturnService service;
    private final AtomicReference<MaterialReturn> stored = new AtomicReference<>();

    @BeforeEach void setUp() {
        mapper = mock(MaterialReturnMapper.class); orders = mock(WorkOrderMapper.class);
        codec = new MaterialReturnOutcomeCodec(new ObjectMapper().findAndRegisterModules());
        writer = new MaterialReturnTransactionService(mapper,orders,codec); service = service(writer);
        order = new WorkOrder(); order.setWorkOrderId(1L); order.setStatus("IN_PROGRESS");
        source = new MaterialConsumption(); source.setConsumptionId(7L); source.setWorkOrderId(1L); source.setRawLotId(3L); source.setConsumedQty(new BigDecimal("60"));
        lot = new RawMaterialLot(); lot.setRawLotId(3L); lot.setUnitCode("EA"); lot.setRegisteredQty(new BigDecimal("100")); lot.setAvailableQty(new BigDecimal("60"));
        when(mapper.claimReceipt(any())).thenAnswer(call -> { ((MaterialReturnReceipt)call.getArgument(0)).setReceiptId(11L); return 1; });
        when(mapper.findConsumption(7L)).thenReturn(source); when(mapper.findConsumptionForUpdate(7L)).thenReturn(source);
        when(orders.findByIdForUpdate(1L)).thenReturn(order); when(mapper.findLotForUpdate(3L)).thenReturn(lot);
        when(mapper.sumReturned(7L)).thenReturn(new BigDecimal("20")); when(mapper.increaseAvailable(eq(3L),any())).thenReturn(1);
        when(mapper.insert(any())).thenAnswer(call -> {
            MaterialReturn row = call.getArgument(0); row.setReturnId(9L); row.setWorkOrderId(1L); row.setRawLotId(3L); row.setUnitCode(lot.getUnitCode());
            row.setReturnedAt(LocalDateTime.of(2026,10,28,1,2,3,123456000)); stored.set(row); return 1;
        });
        when(mapper.findByReceipt(11L)).thenAnswer(call -> stored.get()); when(mapper.finishReceipt(eq(11L),anyString())).thenReturn(1); when(mapper.countReceiptResults(11L)).thenReturn(1);
    }
    @Test void restoresOriginalLotWithoutEditingSourceAndLocksInTheAgreedOrder() {
        var outcome = service.create(MaterialReturnContractTests.form("30.000000"));
        assertThat(outcome.returnedQty()).isEqualTo("30"); assertThat(outcome.availableBefore()).isEqualTo("60"); assertThat(outcome.availableAfter()).isEqualTo("90");
        assertThat(outcome.consumptionId()).isEqualTo(7L); assertThat(outcome.rawLotId()).isEqualTo(3L);
        assertThat(source.getConsumedQty()).isEqualByComparingTo("60");
        var sequence = inOrder(mapper,orders); sequence.verify(mapper).claimReceipt(any()); sequence.verify(mapper).findConsumption(7L);
        sequence.verify(orders).findByIdForUpdate(1L); sequence.verify(mapper).findConsumptionForUpdate(7L); sequence.verify(mapper).findLotForUpdate(3L);
        sequence.verify(mapper).sumReturned(7L); sequence.verify(mapper).increaseAvailable(3L,new BigDecimal("30")); sequence.verify(mapper).insert(any());
    }
    @Test void rejectsBothOriginalAndCumulativeOverReturn() { rejected("61"); rejected("41"); verify(mapper,never()).increaseAvailable(anyLong(),any()); }
    @Test void rejectsAllNonRunningStatesAndMissingReferences() {
        for (String state : new String[]{"READY","COMPLETED","CANCELLED"}) { order.setStatus(state); rejected("1"); }
        when(orders.findByIdForUpdate(1L)).thenReturn(null); rejected("1");
        when(mapper.findConsumption(7L)).thenReturn(null); rejected("1"); verify(mapper,never()).increaseAvailable(anyLong(),any());
    }
    @Test void preservedLotUnitControlsPrecisionWithoutRounding() {
        rejected("1.5");
        for (String unit : new String[]{"M","G"}) {
            lot.setUnitCode(unit); assertThat(service.create(MaterialReturnContractTests.form("0.000001")).returnedQty()).isEqualTo("0.000001"); rejected("0.0000001");
        }
        lot.setUnitCode("KG"); rejected("1");
    }
    @Test void restorationCannotExceedRegisteredStockOrDecimalRange() {
        lot.setAvailableQty(new BigDecimal("100")); rejected("1");
        lot.setRegisteredQty(MaterialReturnCommand.MAX_QTY); lot.setAvailableQty(MaterialReturnCommand.MAX_QTY); rejected("1");
        verify(mapper,never()).increaseAvailable(anyLong(),any());
    }
    @Test void failedConditionalRestoreNeverInsertsReturn() {
        when(mapper.increaseAvailable(eq(3L),any())).thenReturn(0); rejected("1"); verify(mapper,never()).insert(any());
    }
    @Test void changedParentOrMissingLockedSourceFailsBeforeInventoryChanges() {
        var changed = new MaterialConsumption(); changed.setWorkOrderId(99L);
        when(mapper.findConsumptionForUpdate(7L)).thenReturn(changed);
        assertThatThrownBy(() -> service.create(MaterialReturnContractTests.form("1"))).isInstanceOf(DataIntegrityViolationException.class);
        verify(mapper,never()).increaseAvailable(anyLong(),any());
    }
    @Test void replayAfterCompletionReturnsOriginalStockAndRejectsChangedReason() {
        var original = existing(); order.setStatus("COMPLETED"); lot.setAvailableQty(BigDecimal.ZERO);
        doThrow(new DuplicateKeyException("uq_request_key")).when(mapper).claimReceipt(any()); clearInvocations(mapper,orders);
        assertThat(service.create(MaterialReturnContractTests.form("1.000000"))).isEqualTo(original);
        verifyNoInteractions(orders); verify(mapper,never()).increaseAvailable(anyLong(),any()); verify(mapper,never()).insert(any());
        var changed = MaterialReturnContractTests.form("1"); changed.setReason("다른 사유");
        assertThatThrownBy(() -> service.create(changed)).isInstanceOf(MaterialReturnRuleException.class);
    }
    @Test void missingOrWrongReceiptResultDoesNotProduceSuccess() {
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).claimReceipt(any());
        assertThatThrownBy(() -> service.create(MaterialReturnContractTests.form("1"))).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void duplicateAttemptRollsBackBeforeSeparateReadTransaction() {
        var original = existing(); doThrow(new DuplicateKeyException("duplicate")).when(mapper).claimReceipt(any());
        var manager = mock(PlatformTransactionManager.class); var failed = new SimpleTransactionStatus(); var read = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(failed,read);
        assertThat(service(proxy(manager)).create(MaterialReturnContractTests.form("1"))).isEqualTo(original);
        var sequence = inOrder(manager); sequence.verify(manager).getTransaction(any()); sequence.verify(manager).rollback(failed); sequence.verify(manager).getTransaction(any()); sequence.verify(manager).commit(read);
        var definitions = org.mockito.ArgumentCaptor.forClass(TransactionDefinition.class); verify(manager,times(2)).getTransaction(definitions.capture());
        assertThat(definitions.getAllValues()).allMatch(def -> def.getPropagationBehavior()==TransactionDefinition.PROPAGATION_REQUIRES_NEW && def.getIsolationLevel()==TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    @Test void writeFailuresAndLockErrorsRollBackThroughSpringProxy() {
        for (String phase : new String[]{"restore","insert","finish","lock","count"}) {
            setUp(); var manager=mock(PlatformTransactionManager.class); var status=new SimpleTransactionStatus(); when(manager.getTransaction(any())).thenReturn(status);
            if (phase.equals("restore")) doThrow(new DataIntegrityViolationException("restore")).when(mapper).increaseAvailable(anyLong(),any());
            if (phase.equals("insert")) doThrow(new DataIntegrityViolationException("insert")).when(mapper).insert(any());
            if (phase.equals("finish")) when(mapper.finishReceipt(anyLong(),anyString())).thenReturn(0);
            if (phase.equals("lock")) doThrow(new CannotAcquireLockException("lock wait/deadlock")).when(mapper).findConsumptionForUpdate(anyLong());
            if (phase.equals("count")) when(mapper.countReceiptResults(11L)).thenReturn(2);
            assertThatThrownBy(() -> service(proxy(manager)).create(MaterialReturnContractTests.form("1"))).isInstanceOf(DataAccessException.class);
            verify(manager).rollback(status); verify(manager,never()).commit(any());
        }
    }
    @Test void completedHistoryRemainsReadableAndInvalidSnapshotIsRejected() {
        existing(); var row=stored.get(); row.setWorkOrderStatus("COMPLETED"); when(mapper.findById(9L)).thenReturn(row);
        assertThat(service.get(9L).getOutcome().availableAfter()).isEqualTo("61");
        row.setSnapshot("{}"); assertThatThrownBy(() -> service.get(9L)).isInstanceOf(DataIntegrityViolationException.class);
    }
    private MaterialReturnOutcome existing() {
        var outcome=service.create(MaterialReturnContractTests.form("1")); var command=MaterialReturnCommand.from(MaterialReturnContractTests.form("1"),"DEV");
        var receipt=new MaterialReturnReceipt(); receipt.setOperationCode("MATERIAL_RETURN"); receipt.setReceiptId(11L); receipt.setCreatedBy("DEV"); receipt.setPayloadFingerprint(command.fingerprint());
        receipt.setProcessedAt(LocalDateTime.now()); receipt.setSnapshot(codec.encode(outcome)); stored.get().setSnapshot(receipt.getSnapshot());
        when(mapper.findReceipt(command.scopeKey(),command.requestKey())).thenReturn(receipt); return outcome;
    }
    private MaterialReturnTransactionService proxy(PlatformTransactionManager manager) {
        var proxy=new ProxyFactory(writer); proxy.setProxyTargetClass(true); proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource())); return (MaterialReturnTransactionService)proxy.getProxy();
    }
    private MaterialReturnService service(MaterialReturnTransactionService writer) { return new MaterialReturnService(mapper,writer,() -> "DEV",codec); }
    private void rejected(String quantity) { assertThatThrownBy(() -> service.create(MaterialReturnContractTests.form(quantity))).isInstanceOf(MaterialReturnRuleException.class); }
}
