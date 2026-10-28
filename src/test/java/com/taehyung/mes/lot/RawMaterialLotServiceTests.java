package com.taehyung.mes.lot;

import com.taehyung.mes.common.actor.EnvironmentActorProvider;
import com.taehyung.mes.item.Item;
import com.taehyung.mes.item.ItemMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RawMaterialLotServiceTests {
    private RawMaterialLotMapper mapper;
    private ItemMapper items;
    private RawMaterialLotService service;

    @BeforeEach void setUp() {
        mapper = mock(RawMaterialLotMapper.class); items = mock(ItemMapper.class);
        service = new RawMaterialLotService(mapper, items, new EnvironmentActorProvider("LOCAL_DEV"));
        when(items.findByIdForUpdate(1L)).thenReturn(raw("EA", 1));
    }

    @Test void createsLotWithSameInitialQuantitiesAndServerActor() {
        when(mapper.insert(any())).thenAnswer(call -> {
            RawMaterialLot row = call.getArgument(0);
            assertThat(row.getRegisteredQty()).isEqualByComparingTo("10");
            assertThat(row.getAvailableQty()).isEqualByComparingTo(row.getRegisteredQty());
            assertThat(row.getUnitCode()).isEqualTo("EA");
            assertThat(row.getCreatedBy()).isEqualTo("LOCAL_DEV");
            row.setRawLotId(9L); return 1;
        });
        assertThat(service.create(form("10"))).isEqualTo(9L);
        var order = inOrder(items, mapper);
        order.verify(items).findByIdForUpdate(1L);
        order.verify(mapper).existsByLotNo("RAW-001");
        order.verify(mapper).insert(any());
    }

    @ParameterizedTest
    @ValueSource(strings={"0", "-1", "1000000000000", "0.0000001", "1.0000000"})
    void rejectsInvalidQuantityWithoutRounding(String quantity) { rejected(form(quantity)); }

    @Test void rejectsEaFractionAndAcceptsMgWithSixDecimalPlaces() {
        rejected(form("1.5"));
        when(mapper.insert(any())).thenAnswer(call -> { ((RawMaterialLot) call.getArgument(0)).setRawLotId(9L); return 1; });
        for (String unit : List.of("M", "G")) {
            when(items.findByIdForUpdate(1L)).thenReturn(raw(unit, 1));
            assertThat(service.create(form("0.000001"))).isEqualTo(9L);
            assertThat(service.create(form("999999999999.999999"))).isEqualTo(9L);
        }
    }

    @Test void rejectsMissingInactiveNonRawAndUnsupportedUnit() {
        when(items.findByIdForUpdate(1L)).thenReturn(null); rejected(form("10"));
        when(items.findByIdForUpdate(1L)).thenReturn(raw("EA", 0)); rejected(form("10"));
        Item wrongKind = raw("EA", 1); wrongKind.setItemKind("FINISHED");
        when(items.findByIdForUpdate(1L)).thenReturn(wrongKind); rejected(form("10"));
        when(items.findByIdForUpdate(1L)).thenReturn(raw("KG", 1)); rejected(form("10"));
    }

    @Test void rejectsMissingAndInvalidFields() {
        rejected(null);
        RawMaterialLotForm form = form("10"); form.setItemId(null); rejected(form);
        form = form("10"); form.setRegisteredQty(null); rejected(form);
        for (String number : new String[]{null, "", " ", "A".repeat(65), "한글", "A\nB"}) {
            form = form("10"); form.setLotNo(number); rejected(form);
        }
    }

    @Test void detectsGlobalDuplicateAndTranslatesConcurrentUniqueFailure() {
        when(mapper.existsByLotNo("RAW-001")).thenReturn(true); rejected(form("10"));
        var otherItem = form("10"); otherItem.setItemId(2L);
        Item otherRaw = raw("EA", 1); otherRaw.setItemId(2L);
        when(items.findByIdForUpdate(2L)).thenReturn(otherRaw); rejected(otherItem);
        when(mapper.existsByLotNo("RAW-001")).thenReturn(false);
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("uq_raw_lot_no"));
        assertThatThrownBy(() -> service.create(form("10"))).isInstanceOf(RawMaterialLotRuleException.class)
                .hasMessageContaining("LOT 번호");
    }

    @Test void missingActorStopsBeforeDatabaseAccess() {
        for (String actor : new String[]{null, "", " ", "A".repeat(65)}) {
            var invalid = new RawMaterialLotService(mapper, items, new EnvironmentActorProvider(actor));
            assertThatThrownBy(() -> invalid.create(form("10"))).isInstanceOf(RawMaterialLotRuleException.class);
        }
        verifyNoInteractions(items, mapper);
    }

    @Test void readsSearchChoicesDetailsAndReportsQuantityMismatchWithoutWrites() {
        var row = new RawMaterialLot(); row.setRawLotId(9L); row.setRegisteredQty(new BigDecimal("100"));
        row.setAvailableQty(new BigDecimal("60")); row.setConsumedQty(new BigDecimal("60")); row.setReturnedQty(new BigDecimal("20"));
        when(mapper.findAll("RAW", "RM", "자재")).thenReturn(List.of(row));
        when(mapper.findById(9L)).thenReturn(row);
        when(mapper.findActiveRawItems()).thenReturn(List.of(raw("EA", 1)));
        assertThat(service.list("RAW", "RM", "자재")).containsExactly(row);
        assertThat(service.choices()).hasSize(1);
        assertThat(service.get(9L).getNetConsumedQty()).isEqualByComparingTo("40");
        assertThat(row.isQuantityConsistent()).isTrue();
        row.setAvailableQty(new BigDecimal("59")); assertThat(row.isQuantityConsistent()).isFalse();
        row.setAvailableQty(BigDecimal.ZERO); row.setConsumedQty(new BigDecimal("100")); row.setReturnedQty(BigDecimal.ZERO);
        assertThat(service.get(9L).getAvailableQty()).isEqualByComparingTo("0"); assertThat(row.isQuantityConsistent()).isTrue();
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(RawMaterialLotNotFoundException.class);
        assertThatThrownBy(() -> service.get(0L)).isInstanceOf(RawMaterialLotNotFoundException.class);
        assertThatThrownBy(() -> service.list("A".repeat(65), "", "")).isInstanceOf(RawMaterialLotRuleException.class);
        assertThatThrownBy(() -> service.list("", "A".repeat(51), "")).isInstanceOf(RawMaterialLotRuleException.class);
        assertThatThrownBy(() -> service.list("", "", "가".repeat(201))).isInstanceOf(RawMaterialLotRuleException.class);
        verify(mapper, never()).insert(any());
    }

    @Test void failedInsertAndMissingIdNeverReportSuccessAndTransactionRollsBack() {
        when(mapper.insert(any())).thenReturn(0);
        assertThatThrownBy(() -> service.create(form("10"))).isInstanceOf(DataIntegrityViolationException.class);
        when(mapper.insert(any())).thenReturn(1);
        assertThatThrownBy(() -> service.create(form("10"))).isInstanceOf(DataIntegrityViolationException.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        var status = new SimpleTransactionStatus(); when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        var proxy = new ProxyFactory(service); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        when(mapper.insert(any())).thenThrow(new DataIntegrityViolationException("failure"));
        assertThatThrownBy(() -> ((RawMaterialLotService) proxy.getProxy()).create(form("10"))).isInstanceOf(DataIntegrityViolationException.class);
        verify(manager).rollback(status); verify(manager, never()).commit(any());
    }

    private void rejected(RawMaterialLotForm form) {
        assertThatThrownBy(() -> service.create(form)).isInstanceOf(RawMaterialLotRuleException.class);
        verify(mapper, never()).insert(any());
    }
    private Item raw(String unit, int active) {
        var row = new Item(); row.setItemId(1L); row.setItemKind("RAW"); row.setUnitCode(unit); row.setIsActive(active); return row;
    }
    private RawMaterialLotForm form(String quantity) {
        var form = new RawMaterialLotForm(); form.setItemId(1L); form.setLotNo("RAW-001"); form.setRegisteredQty(new BigDecimal(quantity)); return form;
    }
}
