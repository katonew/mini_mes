package com.taehyung.mes.routing;

import com.taehyung.mes.common.actor.EnvironmentActorProvider;
import com.taehyung.mes.item.Item;
import com.taehyung.mes.item.ItemMapper;
import com.taehyung.mes.process.ManufacturingProcess;
import com.taehyung.mes.process.ProcessMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

class RoutingServiceTests {
    private RoutingMapper mapper;
    private ItemMapper items;
    private ProcessMapper processes;
    private RoutingService service;

    @BeforeEach
    void setUp() {
        mapper = mock(RoutingMapper.class); items = mock(ItemMapper.class); processes = mock(ProcessMapper.class);
        service = new RoutingService(mapper, items, processes, new EnvironmentActorProvider("LOCAL_DEV"));
        when(items.findByIdForUpdate(1L)).thenReturn(product("FINISHED", 1));
        when(processes.findByIdForUpdate(2L)).thenReturn(process(2L, "조립", 1));
        when(mapper.insertRouting(any())).thenAnswer(call -> {
            Routing row = call.getArgument(0); row.setRoutingId(10L); return 1;
        });
        when(mapper.insertRevision(any())).thenAnswer(call -> {
            RoutingRevision row = call.getArgument(0); row.setRoutingRevisionId(20L); return 1;
        });
        when(mapper.insertStep(any())).thenReturn(1);
    }

    @Test
    void createsRoutingRevisionAndServerSnapshotAtomically() {
        assertThat(service.create(form())).isEqualTo(20L);
        verify(mapper).insertRouting(argThat(row -> "LOCAL_DEV".equals(row.getCreatedBy())));
        verify(mapper).insertStep(argThat(row -> row.getStepNo() == 10L && row.getProcessId() == 2L
                && "조립".equals(row.getProcessNameSnapshot()) && "LOCAL_DEV".equals(row.getCreatedBy())));
    }

    @Test
    void addsRevisionWithoutReplacingHeader() {
        when(mapper.findRoutingForUpdate(10L)).thenReturn(header());
        assertThat(service.addRevision(10L, form())).isEqualTo(20L);
        verify(mapper, never()).insertRouting(any());
    }

    @Test
    void missingWrongKindAndInactiveProductAreRejected() {
        when(items.findByIdForUpdate(1L)).thenReturn(null);
        rejected(form());
        when(items.findByIdForUpdate(1L)).thenReturn(product("RAW", 1));
        rejected(form());
        when(items.findByIdForUpdate(1L)).thenReturn(product("FINISHED", 0));
        rejected(form());
    }

    @Test
    void missingAndInactiveProcessAreRejectedBeforeInsert() {
        when(processes.findByIdForUpdate(2L)).thenReturn(null);
        rejected(form());
        when(processes.findByIdForUpdate(2L)).thenReturn(process(2L, "조립", 0));
        rejected(form());
    }

    @Test
    void missingOrDuplicateOrderAndOutOfRangeRevisionAreRejected() {
        RoutingForm form = form(); form.getSteps().add(step(3L, 10L)); rejected(form);
        form = form(); form.getSteps().get(0).setStepNo(0L); rejected(form);
        form = form(); form.setRevisionNo(4_294_967_296L); rejected(form);
        form = form(); form.getSteps().clear(); rejected(form);
    }

    @Test
    void uniqueCodeAndRevisionPrecheckAndConcurrentCollisionAreHandled() {
        when(mapper.existsByCode("ROUTE-001")).thenReturn(true); rejected(form());
        when(mapper.existsByCode("ROUTE-001")).thenReturn(false);
        doThrow(new DuplicateKeyException("code")).when(mapper).insertRouting(any());
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(RoutingRuleException.class).hasMessageContaining("중복");
        when(mapper.findRoutingForUpdate(10L)).thenReturn(header());
        when(mapper.existsRevision(10L, 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.addRevision(10L, form())).isInstanceOf(RoutingRuleException.class);
        when(mapper.existsRevision(10L, 1L)).thenReturn(false);
        doThrow(new DuplicateKeyException("revision")).when(mapper).insertRevision(any());
        assertThatThrownBy(() -> service.addRevision(10L, form())).isInstanceOf(RoutingRuleException.class).hasMessageContaining("중복");
    }

    @Test
    void existingSnapshotSurvivesCurrentNameChangeAndNewRevisionUsesCurrentName() {
        RoutingRevision original = new RoutingRevision(); original.setRoutingRevisionId(20L);
        RoutingStep saved = new RoutingStep(); saved.setProcessNameSnapshot("과거 조립"); saved.setProcessId(2L);
        when(mapper.findRevision(20L)).thenReturn(original);
        when(mapper.findSteps(20L)).thenReturn(List.of(saved));
        when(processes.findByIdForUpdate(2L)).thenReturn(process(2L, "변경 조립", 1));
        assertThat(service.get(20L).getSteps().get(0).getProcessNameSnapshot()).isEqualTo("과거 조립");
        service.create(form());
        verify(mapper).insertStep(argThat(row -> "변경 조립".equals(row.getProcessNameSnapshot())));
    }

    @Test
    void locksHeaderThenProductThenProcessesInAscendingIdOrder() {
        when(mapper.findRoutingForUpdate(10L)).thenReturn(header());
        when(processes.findByIdForUpdate(3L)).thenReturn(process(3L, "검사", 1));
        RoutingForm form = form(); form.getSteps().add(0, step(3L, 5L));
        service.addRevision(10L, form);
        var order = inOrder(mapper, items, processes);
        order.verify(mapper).findRoutingForUpdate(10L);
        order.verify(items).findByIdForUpdate(1L);
        order.verify(processes).findByIdForUpdate(2L);
        order.verify(processes).findByIdForUpdate(3L);
    }

    @Test
    void laterStepFailureRequestsRollbackThroughSpringProxy() {
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        var status = new SimpleTransactionStatus();
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        ProxyFactory factory = new ProxyFactory(service); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        when(processes.findByIdForUpdate(3L)).thenReturn(process(3L, "검사", 1));
        when(mapper.insertStep(any())).thenReturn(1).thenThrow(new DataIntegrityViolationException("second step"));
        RoutingForm form = form(); form.getSteps().add(step(3L, 20L));
        assertThatThrownBy(() -> ((RoutingService) factory.getProxy()).create(form)).isInstanceOf(DataIntegrityViolationException.class);
        verify(transactions).rollback(status); verify(transactions, never()).commit(any());
    }

    @Test
    void searchesAndMissingRevisionReturnsNotFound() {
        when(mapper.findAll("FG", "제품")).thenReturn(List.of());
        assertThat(service.list("FG", "제품")).isEmpty();
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(RoutingNotFoundException.class);
    }

    @Test
    void missingActorAndForgedHeaderAreRejected() {
        service = new RoutingService(mapper, items, processes, new EnvironmentActorProvider("")); rejected(form());
        service = new RoutingService(mapper, items, processes, new EnvironmentActorProvider("LOCAL_DEV"));
        when(mapper.findRoutingForUpdate(10L)).thenReturn(header());
        RoutingForm form = form(); form.setProductItemId(99L);
        assertThatThrownBy(() -> service.addRevision(10L, form)).isInstanceOf(RoutingRuleException.class);
    }

    private void rejected(RoutingForm form) {
        assertThatThrownBy(() -> service.create(form)).isInstanceOf(RoutingRuleException.class);
        verify(mapper, never()).insertRouting(any());
    }
    private Item product(String kind, int active) {
        Item item = new Item(); item.setItemId(1L); item.setItemKind(kind); item.setIsActive(active); return item;
    }
    private ManufacturingProcess process(Long id, String name, int active) {
        var process = new ManufacturingProcess(); process.setProcessId(id); process.setProcessName(name); process.setIsActive(active); return process;
    }
    private Routing header() {
        Routing routing = new Routing(); routing.setRoutingId(10L); routing.setRoutingCode("ROUTE-001"); routing.setProductItemId(1L); return routing;
    }
    private RoutingStepForm step(Long processId, Long stepNo) {
        RoutingStepForm row = new RoutingStepForm(); row.setProcessId(processId); row.setStepNo(stepNo); return row;
    }
    private RoutingForm form() {
        RoutingForm form = new RoutingForm(); form.setRoutingCode("ROUTE-001"); form.setProductItemId(1L); form.setRevisionNo(1L);
        form.setSteps(new ArrayList<>(List.of(step(2L, 10L)))); return form;
    }
}
