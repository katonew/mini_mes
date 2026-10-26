package com.taehyung.mes.workorder;

import com.taehyung.mes.bom.*;
import com.taehyung.mes.routing.*;
import com.taehyung.mes.item.*;
import com.taehyung.mes.process.*;
import com.taehyung.mes.production.ProductionPlan;
import com.taehyung.mes.common.actor.EnvironmentActorProvider;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkOrderServiceTests {
    private WorkOrderMapper mapper;
    private BomMapper boms;
    private RoutingMapper routings;
    private ItemMapper items;
    private ProcessMapper processes;
    private WorkOrderService service;
    private ProductionPlan plan;
    private Bom bom;
    private BomVersion version;
    private Routing routing;
    private RoutingRevision revision;

    @BeforeEach
    void setUp() {
        mapper = mock(WorkOrderMapper.class); boms = mock(BomMapper.class); routings = mock(RoutingMapper.class);
        items = mock(ItemMapper.class); processes = mock(ProcessMapper.class);
        service = new WorkOrderService(mapper, boms, routings, items, processes, new EnvironmentActorProvider("LOCAL_DEV"));
        plan = new ProductionPlan(); plan.setProductionPlanId(1L); plan.setProductItemId(2L); plan.setPlannedQty(new BigDecimal("10"));
        when(mapper.findPlanForUpdate(1L)).thenReturn(plan);
        bom = new Bom(); bom.setBomId(3L); bom.setProductItemId(2L);
        version = new BomVersion(); version.setBomVersionId(4L); version.setBomId(3L); version.setBaseQty(BigDecimal.ONE); version.setProductUnitCode("EA");
        when(boms.findVersion(4L)).thenReturn(version); when(boms.findBomForUpdate(3L)).thenReturn(bom); when(boms.findVersionForUpdate(4L)).thenReturn(version);
        routing = new Routing(); routing.setRoutingId(5L); routing.setProductItemId(2L);
        revision = new RoutingRevision(); revision.setRoutingRevisionId(6L); revision.setRoutingId(5L);
        when(routings.findRevision(6L)).thenReturn(revision); when(routings.findRoutingForUpdate(5L)).thenReturn(routing);
        when(mapper.findRevisionForUpdate(6L)).thenReturn(revision);
        Item product = new Item(); product.setItemId(2L); product.setItemKind("FINISHED"); product.setUnitCode("EA"); product.setIsActive(1);
        when(items.findByIdForUpdate(2L)).thenReturn(product);
        Item raw = new Item(); raw.setItemId(7L); raw.setItemKind("RAW"); raw.setUnitCode("EA"); raw.setIsActive(1);
        when(items.findByIdForUpdate(7L)).thenReturn(raw);
        BomComponent component = new BomComponent(); component.setMaterialItemId(7L); component.setMaterialUnitCode("EA"); component.setRequiredQty(BigDecimal.ONE);
        when(boms.findComponents(4L)).thenReturn(List.of(component));
        RoutingStep step = new RoutingStep(); step.setProcessId(8L); step.setStepNo(10L); step.setProcessNameSnapshot("과거 조립");
        when(routings.findSteps(6L)).thenReturn(List.of(step));
        ManufacturingProcess process = new ManufacturingProcess(); process.setProcessId(8L); process.setIsActive(1); process.setProcessName("현재 조립");
        when(processes.findByIdForUpdate(8L)).thenReturn(process);
        when(mapper.markBomUsed(4L)).thenReturn(1); when(mapper.markRoutingUsed(6L)).thenReturn(1);
        when(mapper.insert(any())).thenAnswer(call -> { WorkOrder row = call.getArgument(0); row.setWorkOrderId(9L); return 1; });
    }
    @Test
    void createsExactReferencesAndMarksFirstUseWithoutCappingPlanAllocation() {
        assertThat(service.create(form())).isEqualTo(9L);
        verify(mapper).insert(argThat(row -> row.getBomVersionId() == 4L && row.getRoutingRevisionId() == 6L
                && row.getProductItemId() == 2L && row.getOrderedQty().compareTo(new BigDecimal("20")) == 0
                && "LOCAL_DEV".equals(row.getCreatedBy()) && "EA".equals(row.getProductUnitCode())));
        verify(mapper).markBomUsed(4L); verify(mapper).markRoutingUsed(6L);
        var order = inOrder(mapper, boms, routings, items, processes);
        order.verify(mapper).findPlanForUpdate(1L); order.verify(boms).findVersion(4L); order.verify(routings).findRevision(6L);
        order.verify(boms).findBomForUpdate(3L); order.verify(routings).findRoutingForUpdate(5L);
        order.verify(boms).findVersionForUpdate(4L); order.verify(mapper).findRevisionForUpdate(6L);
        order.verify(items).findByIdForUpdate(2L); order.verify(items).findByIdForUpdate(7L); order.verify(processes).findByIdForUpdate(8L);
    }
    @Test
    void repeatedRegistrationCanCreateMultipleOrdersAndExistingUseTimestampsArePreserved() {
        version.setFirstUsedAt(LocalDateTime.of(2026, 1, 1, 0, 0)); revision.setFirstUsedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        service.create(form()); service.create(form());
        verify(mapper, times(2)).insert(any()); verify(mapper, never()).markBomUsed(any()); verify(mapper, never()).markRoutingUsed(any());
    }
    @Test
    void planBomAndRoutingProductMismatchAreRejected() {
        WorkOrderForm forged = form(); forged.setProductItemId(99L); rejected(forged);
        bom.setProductItemId(99L); rejected(form()); bom.setProductItemId(2L);
        routing.setProductItemId(99L); rejected(form());
    }
    @Test
    void missingVersionsAndInactiveProductOrProcessAreRejected() {
        when(boms.findVersion(4L)).thenReturn(null); rejected(form()); when(boms.findVersion(4L)).thenReturn(version);
        when(routings.findRevision(6L)).thenReturn(null); rejected(form()); when(routings.findRevision(6L)).thenReturn(revision);
        Item inactive = new Item(); inactive.setItemKind("FINISHED"); inactive.setUnitCode("EA"); inactive.setIsActive(0);
        when(items.findByIdForUpdate(2L)).thenReturn(inactive); rejected(form()); inactive.setIsActive(1);
        ManufacturingProcess process = new ManufacturingProcess(); process.setIsActive(0);
        when(processes.findByIdForUpdate(8L)).thenReturn(process); rejected(form());
    }
    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.5", "1.0000001", "1000000000000"})
    void invalidQuantityNeverTouchesDatabase(String value) {
        WorkOrderForm form = form(); form.setOrderedQty(new BigDecimal(value)); rejected(form);
        verifyNoInteractions(boms, routings, items, processes);
    }
    @Test
    void readyStartsOrCancelsOnlyOnceWithoutRevalidatingCurrentProcessActivation() {
        WorkOrder order = ready(); when(mapper.findByIdForUpdate(9L)).thenReturn(order);
        when(mapper.start(9L, "LOCAL_DEV")).thenReturn(1); when(mapper.cancel(9L, "LOCAL_DEV")).thenReturn(1);
        service.transition(9L, action("START")); verify(mapper).start(9L, "LOCAL_DEV");
        order.setStatus("IN_PROGRESS"); assertThatThrownBy(() -> service.transition(9L, action("START"))).isInstanceOf(WorkOrderRuleException.class);
        assertThatThrownBy(() -> service.transition(9L, action("CANCEL"))).isInstanceOf(WorkOrderRuleException.class);
        order.setStatus("READY"); service.transition(9L, action("CANCEL")); verify(mapper).cancel(9L, "LOCAL_DEV");
        verifyNoInteractions(processes, items, boms, routings);
    }
    @Test
    void usedHistoryAndTerminalStatesCannotBeCancelledOrRestarted() {
        WorkOrder order = ready(); when(mapper.findByIdForUpdate(9L)).thenReturn(order);
        when(mapper.hasExecutionHistory(9L)).thenReturn(true);
        assertThatThrownBy(() -> service.transition(9L, action("CANCEL"))).isInstanceOf(WorkOrderRuleException.class);
        when(mapper.hasExecutionHistory(9L)).thenReturn(false);
        for (String state : List.of("COMPLETED", "CANCELLED")) {
            order.setStatus(state);
            assertThatThrownBy(() -> service.transition(9L, action("START"))).isInstanceOf(WorkOrderRuleException.class);
        }
        verify(mapper, never()).start(any(), any()); verify(mapper, never()).cancel(any(), any());
    }
    @Test
    void noResultsCannotPassCompletionGuardAndCompleteActionIsUnavailable() {
        WorkOrder order = ready(); order.setStatus("IN_PROGRESS"); when(mapper.findByIdForUpdate(9L)).thenReturn(order);
        WorkOrderTotals totals = new WorkOrderTotals(); totals.setResultCount(0L); totals.setProducedQty(BigDecimal.ZERO);
        when(mapper.findTotals(9L)).thenReturn(totals);
        assertThatThrownBy(() -> service.validateCompletion(9L)).isInstanceOf(WorkOrderRuleException.class);
        assertThatThrownBy(() -> service.transition(9L, action("COMPLETE"))).isInstanceOf(WorkOrderRuleException.class);
    }
    @Test
    void failedInsertPropagatesForTransactionRollbackAndMissingOrderIs404() {
        doThrow(new DataIntegrityViolationException("insert failure")).when(mapper).insert(any());
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(WorkOrderNotFoundException.class);
    }
    @Test
    void searchesAndReturnsDetailsUsingStoredRoutingSnapshot() {
        when(mapper.findAll(9L, 1L, "제품", "READY")).thenReturn(List.of(ready()));
        assertThat(service.list("9", "1", "제품", "READY")).hasSize(1);
        WorkOrder order = ready(); order.setRoutingRevisionId(6L); when(mapper.findById(9L)).thenReturn(order);
        WorkOrderTotals totals = new WorkOrderTotals(); totals.setResultCount(0L); totals.setProducedQty(BigDecimal.ZERO);
        totals.setGoodQty(BigDecimal.ZERO); totals.setDefectQty(BigDecimal.ZERO); when(mapper.findTotals(9L)).thenReturn(totals);
        assertThat(service.get(9L).getSteps().get(0).getProcessNameSnapshot()).isEqualTo("과거 조립");
        assertThatThrownBy(() -> service.list("bad", "", "", "")).isInstanceOf(WorkOrderRuleException.class);
    }
    @Test
    void optionsShowInactiveProcessesAsUnselectableAndCompletionGuardRequiresExactQuantity() {
        when(mapper.findPlan(1L)).thenReturn(plan);
        Item product = new Item(); product.setItemKind("FINISHED"); product.setIsActive(1); product.setUnitCode("EA"); when(items.findById(2L)).thenReturn(product);
        WorkOrderRoutingOption option = new WorkOrderRoutingOption(); option.setStepCount(1L); option.setInactiveProcessCount(1L);
        when(mapper.findRoutingOptions(2L)).thenReturn(List.of(option));
        assertThat(service.options(1L).getRoutings().get(0).isSelectable()).isFalse();
        option.setInactiveProcessCount(0L); assertThat(service.options(1L).getRoutings().get(0).isSelectable()).isTrue();
        WorkOrder order = ready(); order.setStatus("IN_PROGRESS"); when(mapper.findByIdForUpdate(9L)).thenReturn(order);
        WorkOrderTotals totals = new WorkOrderTotals(); totals.setResultCount(1L); totals.setProducedQty(new BigDecimal("19")); when(mapper.findTotals(9L)).thenReturn(totals);
        assertThatThrownBy(() -> service.validateCompletion(9L)).isInstanceOf(WorkOrderRuleException.class);
        totals.setProducedQty(new BigDecimal("20")); service.validateCompletion(9L);
        totals.setProducedQty(new BigDecimal("21")); assertThatThrownBy(() -> service.validateCompletion(9L)).isInstanceOf(WorkOrderRuleException.class);
        verify(mapper, never()).start(any(), any()); verify(mapper, never()).cancel(any(), any());
    }
    @Test
    void failedRegistrationRequestsSpringRollbackInsteadOfCommittingUseMarkers() {
        var manager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        var status = new org.springframework.transaction.support.SimpleTransactionStatus();
        when(manager.getTransaction(any(org.springframework.transaction.TransactionDefinition.class))).thenReturn(status);
        var proxy = new org.springframework.aop.framework.ProxyFactory(service); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager, new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        doThrow(new DataIntegrityViolationException("failed insert")).when(mapper).insert(any());
        assertThatThrownBy(() -> ((WorkOrderService) proxy.getProxy()).create(form())).isInstanceOf(DataIntegrityViolationException.class);
        verify(manager).rollback(status); verify(manager, never()).commit(any());
    }
    private void rejected(WorkOrderForm form) { assertThatThrownBy(() -> service.create(form)).isInstanceOf(WorkOrderRuleException.class); verify(mapper, never()).insert(any()); }
    private WorkOrder ready() { WorkOrder row = new WorkOrder(); row.setWorkOrderId(9L); row.setStatus("READY"); row.setOrderedQty(new BigDecimal("20")); return row; }
    private WorkOrderTransitionForm action(String value) { var form = new WorkOrderTransitionForm(); form.setAction(value); return form; }
    private WorkOrderForm form() { var form = new WorkOrderForm(); form.setProductionPlanId(1L); form.setProductItemId(2L); form.setBomVersionId(4L); form.setRoutingRevisionId(6L); form.setOrderedQty(new BigDecimal("20")); return form; }
}
