package com.taehyung.mes.production;

import com.taehyung.mes.common.actor.EnvironmentActorProvider;
import com.taehyung.mes.item.Item;
import com.taehyung.mes.item.ItemMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductionPlanServiceTests {
    private ProductionPlanMapper mapper;
    private ItemMapper items;
    private ProductionPlanService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProductionPlanMapper.class); items = mock(ItemMapper.class);
        service = new ProductionPlanService(mapper, items, new EnvironmentActorProvider("LOCAL_DEV"));
        when(items.findByIdForUpdate(1L)).thenReturn(product("FINISHED", 1));
    }

    @Test
    void createsPositivePlanWithServerActorAndGeneratedId() {
        when(mapper.insert(any())).thenAnswer(call -> {
            ProductionPlan row = call.getArgument(0);
            assertThat(row.getCreatedBy()).isEqualTo("LOCAL_DEV");
            assertThat(row.getPlannedQty()).isEqualByComparingTo("100");
            assertThat(row.getPlannedDate()).isEqualTo(LocalDate.of(2026, 11, 1));
            row.setProductionPlanId(10L); return 1;
        });
        assertThat(service.create(form())).isEqualTo(10L);
        verify(items).findByIdForUpdate(1L);
    }

    @Test
    void missingWrongKindAndInactiveProductAreRejected() {
        when(items.findByIdForUpdate(1L)).thenReturn(null); rejected(form());
        when(items.findByIdForUpdate(1L)).thenReturn(product("RAW", 1)); rejected(form());
        when(items.findByIdForUpdate(1L)).thenReturn(product("FINISHED", 0)); rejected(form());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.5", "1.0000001", "1000000000000", "999999999999.999999"})
    void invalidQuantitiesAreNotRoundedOrTruncated(String value) {
        ProductionPlanForm form = form(); form.setPlannedQty(new BigDecimal(value)); rejected(form);
        verifyNoInteractions(items);
    }

    @Test
    void missingQuantityAndProductAreRejected() {
        ProductionPlanForm form = form(); form.setPlannedQty(null); rejected(form);
        form = form(); form.setProductItemId(null); rejected(form);
    }

    @Test
    void missingOrOutOfMysqlRangeDateIsRejectedWithoutInventingPastDatePolicy() {
        ProductionPlanForm form = form(); form.setPlannedDate(null); rejected(form);
        form.setPlannedDate(LocalDate.of(999, 12, 31)); rejected(form);
        form.setPlannedDate(LocalDate.of(10000, 1, 1)); rejected(form);
        form.setPlannedDate(LocalDate.of(1000, 1, 1));
        when(mapper.insert(any())).thenAnswer(call -> { ((ProductionPlan) call.getArgument(0)).setProductionPlanId(10L); return 1; });
        assertThat(service.create(form)).isEqualTo(10L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void invalidActorBlocksInsert(String actor) {
        service = new ProductionPlanService(mapper, items, new EnvironmentActorProvider(actor)); rejected(form());
        verifyNoInteractions(items);
    }

    @Test
    void listSearchesByRealPrimaryKeyAndProductWithoutInventingPlanCode() {
        ProductionPlan row = new ProductionPlan(); row.setProductionPlanId(10L);
        when(mapper.findAll(10L, "FG", "제품")).thenReturn(List.of(row));
        assertThat(service.list("10", "FG", "제품")).containsExactly(row);
        when(mapper.findById(10L)).thenReturn(row);
        assertThat(service.get(10L)).isSameAs(row);
        assertThatThrownBy(() -> service.list("bad", "", "")).isInstanceOf(ProductionPlanRuleException.class);
        assertThatThrownBy(() -> service.list("9223372036854775808", "", "")).isInstanceOf(ProductionPlanRuleException.class);
    }

    @Test
    void missingPlanReturnsNotFound() {
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ProductionPlanNotFoundException.class);
        assertThatThrownBy(() -> service.get(0L)).isInstanceOf(ProductionPlanNotFoundException.class);
    }

    @Test
    void failedWriteAndMissingGeneratedIdNeverReportSuccess() {
        when(mapper.insert(any())).thenReturn(0);
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(DataIntegrityViolationException.class);
        when(mapper.insert(any())).thenReturn(1);
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void springTransactionRollsBackWhenInsertFails() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        var status = new SimpleTransactionStatus(); when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        ProxyFactory proxy = new ProxyFactory(service); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        when(mapper.insert(any())).thenThrow(new DataIntegrityViolationException("storage failure"));
        assertThatThrownBy(() -> ((ProductionPlanService) proxy.getProxy()).create(form())).isInstanceOf(DataIntegrityViolationException.class);
        verify(manager).rollback(status); verify(manager, never()).commit(any());
    }

    private void rejected(ProductionPlanForm form) {
        assertThatThrownBy(() -> service.create(form)).isInstanceOf(ProductionPlanRuleException.class);
        verify(mapper, never()).insert(any());
    }
    private Item product(String kind, int active) {
        Item row = new Item(); row.setItemId(1L); row.setItemKind(kind); row.setIsActive(active); row.setUnitCode("EA"); return row;
    }
    private ProductionPlanForm form() {
        ProductionPlanForm form = new ProductionPlanForm(); form.setProductItemId(1L);
        form.setPlannedQty(new BigDecimal("100")); form.setPlannedDate(LocalDate.of(2026, 11, 1)); return form;
    }
}
