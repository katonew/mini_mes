package com.taehyung.mes.bom;

import com.taehyung.mes.common.actor.EnvironmentActorProvider;
import com.taehyung.mes.item.Item;
import com.taehyung.mes.item.ItemMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BomServiceTests {
    private BomMapper mapper;
    private ItemMapper items;
    private BomService service;

    @BeforeEach
    void setUp() {
        mapper = mock(BomMapper.class);
        items = mock(ItemMapper.class);
        service = new BomService(mapper, items, new EnvironmentActorProvider("LOCAL_DEV"));
        when(items.findByIdForUpdate(1L)).thenReturn(item(1L, "FINISHED", "EA", 1));
        when(items.findByIdForUpdate(2L)).thenReturn(item(2L, "RAW", "EA", 1));
        when(mapper.insertBom(any())).thenAnswer(call -> {
            Bom bom = call.getArgument(0);
            assertThat(bom.getCreatedBy()).isEqualTo("LOCAL_DEV");
            bom.setBomId(10L);
            return 1;
        });
        when(mapper.insertVersion(any())).thenAnswer(call -> {
            BomVersion version = call.getArgument(0);
            assertThat(version.getBaseQty()).isEqualByComparingTo("1");
            assertThat(version.getProductUnitCode()).isEqualTo("EA");
            assertThat(version.getCreatedBy()).isEqualTo("LOCAL_DEV");
            version.setBomVersionId(20L);
            return 1;
        });
        when(mapper.insertComponent(any())).thenReturn(1);
    }

    @Test
    void createsBomVersionAndComponentsWithServerActor() {
        assertThat(service.create(form())).isEqualTo(20L);
        verify(mapper).insertComponent(argThat(row -> row.getMaterialItemId() == 2L
                && row.getRequiredQty().compareTo(new BigDecimal("2")) == 0
                && "EA".equals(row.getMaterialUnitCode()) && "LOCAL_DEV".equals(row.getCreatedBy())));
    }

    @Test
    void missingProductAndWrongKindsAreRejected() {
        when(items.findByIdForUpdate(1L)).thenReturn(null);
        rejectCreate(form());
        when(items.findByIdForUpdate(1L)).thenReturn(item(1L, "RAW", "EA", 1));
        rejectCreate(form());
        when(items.findByIdForUpdate(1L)).thenReturn(item(1L, "FINISHED", "EA", 1));
        when(items.findByIdForUpdate(2L)).thenReturn(item(2L, "FINISHED", "EA", 1));
        rejectCreate(form());
    }

    @Test
    void inactiveProductOrNewMaterialAndNonEaProductAreRejected() {
        when(items.findByIdForUpdate(1L)).thenReturn(item(1L, "FINISHED", "EA", 0));
        rejectCreate(form());
        when(items.findByIdForUpdate(1L)).thenReturn(item(1L, "FINISHED", "M", 1));
        rejectCreate(form());
        when(items.findByIdForUpdate(1L)).thenReturn(item(1L, "FINISHED", "EA", 1));
        when(items.findByIdForUpdate(2L)).thenReturn(item(2L, "RAW", "EA", 0));
        rejectCreate(form());
    }

    @Test
    void duplicateAndEmptyComponentsAreRejected() {
        BomForm form = form();
        form.getComponents().add(componentForm(null, 2L, "3"));
        rejectCreate(form);
        form.setComponents(new ArrayList<>());
        rejectCreate(form);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.5", "1.0000001", "1000000000000"})
    void invalidEaQuantitiesAreRejected(String quantity) {
        BomForm form = form();
        form.getComponents().get(0).setRequiredQty(new BigDecimal(quantity));
        rejectCreate(form);
    }

    @ParameterizedTest
    @ValueSource(strings = {"M", "G"})
    void decimalUnitsAcceptMinimumAndMaximum(String unit) {
        when(items.findByIdForUpdate(2L)).thenReturn(item(2L, "RAW", unit, 1));
        BomForm form = form();
        form.getComponents().get(0).setRequiredQty(new BigDecimal("0.000001"));
        service.create(form);
        form.getComponents().get(0).setRequiredQty(new BigDecimal("999999999999.999999"));
        service.create(form);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0000001", "1000000000000", "-0.000001"})
    void decimalPrecisionAndRangeAreNotRounded(String quantity) {
        when(items.findByIdForUpdate(2L)).thenReturn(item(2L, "RAW", "M", 1));
        BomForm form = form();
        form.getComponents().get(0).setRequiredQty(new BigDecimal(quantity));
        rejectCreate(form);
    }

    @Test
    void duplicateCodeAndVersionIncludingDatabaseRaceAreRejected() {
        when(mapper.existsByCode("BOM-001")).thenReturn(true);
        rejectCreate(form());
        when(mapper.existsByCode("BOM-001")).thenReturn(false);
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).insertBom(any());
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(BomRuleException.class);
        when(mapper.findBomForUpdate(10L)).thenReturn(header());
        when(mapper.existsVersion(10L, 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.addVersion(10L, form())).isInstanceOf(BomRuleException.class);
        when(mapper.existsVersion(10L, 1L)).thenReturn(false);
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).insertVersion(any());
        assertThatThrownBy(() -> service.addVersion(10L, form())).isInstanceOf(BomRuleException.class);
    }

    @Test
    void addsVersionWithoutChangingHeader() {
        when(mapper.findBomForUpdate(10L)).thenReturn(header());
        assertThat(service.addVersion(10L, form())).isEqualTo(20L);
        verify(mapper, never()).insertBom(any());
    }

    @Test
    void rejectsUsedVersionEvenIfOnlyWorkOrderReferenceRemains() {
        BomVersion version = editable();
        BomForm form = BomForm.from(version);
        version.setFirstUsedAt(LocalDateTime.now());
        assertThatThrownBy(() -> service.update(20L, form)).isInstanceOf(BomRuleException.class);
        version.setFirstUsedAt(null);
        when(mapper.hasWorkOrders(20L)).thenReturn(true);
        assertThatThrownBy(() -> service.update(20L, form)).isInstanceOf(BomRuleException.class);
        verify(mapper, never()).updateQuantity(anyLong(), anyLong(), any());
    }

    @Test
    void updatesQuantityAndAppendsWithoutDeletingExistingRows() {
        BomVersion version = editable();
        BomForm form = BomForm.from(version);
        form.getComponents().get(0).setRequiredQty(new BigDecimal("4"));
        form.getComponents().add(componentForm(null, 3L, "0.5"));
        when(items.findByIdForUpdate(3L)).thenReturn(item(3L, "RAW", "G", 1));
        when(mapper.updateQuantity(20L, 30L, new BigDecimal("4"))).thenReturn(1);
        service.update(20L, form);
        verify(mapper).updateQuantity(20L, 30L, new BigDecimal("4"));
        verify(mapper).insertComponent(argThat(row -> row.getLineNo() == 2L && row.getMaterialItemId() == 3L));
    }

    @Test
    void staleSubmissionCannotOverwriteCurrentQuantity() {
        BomVersion version = editable();
        BomForm stale = BomForm.from(version);
        version.getComponents().get(0).setRequiredQty(new BigDecimal("5"));
        assertThatThrownBy(() -> service.update(20L, stale)).isInstanceOf(BomRuleException.class)
                .hasMessageContaining("변경");
        verify(mapper, never()).updateQuantity(anyLong(), anyLong(), any());
    }

    @Test
    void deletionReplacementAndImmutableFieldTamperingAreRejected() {
        BomVersion version = editable();
        BomForm removed = BomForm.from(version);
        removed.getComponents().clear();
        removed.getComponents().add(componentForm(null, 2L, "3"));
        assertThatThrownBy(() -> service.update(20L, removed)).isInstanceOf(BomRuleException.class);
        BomForm replaced = BomForm.from(version);
        replaced.getComponents().get(0).setMaterialItemId(3L);
        assertThatThrownBy(() -> service.update(20L, replaced)).isInstanceOf(BomRuleException.class);
        BomForm changedVersion = BomForm.from(version);
        changedVersion.setVersionNo(2L);
        assertThatThrownBy(() -> service.update(20L, changedVersion)).isInstanceOf(BomRuleException.class);
        verify(mapper, never()).updateQuantity(anyLong(), anyLong(), any());
    }

    @Test
    void existingInactiveMaterialIsPreservedAndPureQuantityEditDoesNotRequireActor() {
        BomVersion version = editable();
        when(items.findByIdForUpdate(2L)).thenReturn(item(2L, "RAW", "EA", 0));
        service = new BomService(mapper, items, new EnvironmentActorProvider(""));
        BomForm form = BomForm.from(version);
        form.getComponents().get(0).setRequiredQty(new BigDecimal("3"));
        when(mapper.updateQuantity(20L, 30L, new BigDecimal("3"))).thenReturn(1);
        service.update(20L, form);
        verify(mapper).updateQuantity(20L, 30L, new BigDecimal("3"));
    }

    @Test
    void listsAndLoadsDetailIncludingHistoricalComponents() {
        BomVersion version = editable();
        when(mapper.findAll("FG", "센서")).thenReturn(List.of(version));
        assertThat(service.list("FG", "센서")).containsExactly(version);
        assertThat(service.get(20L).getComponents()).hasSize(1);
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(BomNotFoundException.class);
    }

    @Test
    void missingActorAndFailedWritesNeverReportSuccess() {
        service = new BomService(mapper, items, new EnvironmentActorProvider(""));
        rejectCreate(form());
        service = new BomService(mapper, items, new EnvironmentActorProvider("LOCAL_DEV"));
        when(mapper.insertComponent(any())).thenReturn(0);
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void failedComponentWriteRollsBackThroughSpringTransactionBoundary() {
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        ProxyFactory factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        BomService transactionalService = (BomService) factory.getProxy();
        doThrow(new DataIntegrityViolationException("component failed")).when(mapper).insertComponent(any());
        assertThatThrownBy(() -> transactionalService.create(form())).isInstanceOf(DataIntegrityViolationException.class);
        verify(transactions).rollback(status);
        verify(transactions, never()).commit(any());
    }

    @Test
    void unchangedUpdateDoesNotWriteAndMissingVersionCannotBeUpdated() {
        BomForm form = BomForm.from(editable());
        service.update(20L, form);
        service.update(20L, form);
        verify(mapper, never()).updateQuantity(anyLong(), anyLong(), any());
        verify(mapper, never()).insertComponent(any());
        assertThatThrownBy(() -> service.update(99L, form)).isInstanceOf(BomNotFoundException.class);
    }

    @Test
    void unsignedVersionRangeAndMissingMaterialAreValidated() {
        BomForm form = form();
        form.setVersionNo(4_294_967_296L);
        rejectCreate(form);
        form.setVersionNo(0L);
        rejectCreate(form);
        form.setVersionNo(1L);
        when(items.findByIdForUpdate(2L)).thenReturn(null);
        rejectCreate(form);
    }

    @Test
    void relatedItemsAreLockedInAscendingOrder() {
        BomForm form = form();
        form.getComponents().add(0, componentForm(null, 3L, "1"));
        when(items.findByIdForUpdate(3L)).thenReturn(item(3L, "RAW", "EA", 1));
        service.create(form);
        var order = inOrder(items);
        order.verify(items).findByIdForUpdate(1L);
        order.verify(items).findByIdForUpdate(2L);
        order.verify(items).findByIdForUpdate(3L);
    }

    private void rejectCreate(BomForm form) {
        assertThatThrownBy(() -> service.create(form)).isInstanceOf(BomRuleException.class);
        verify(mapper, never()).insertBom(any());
    }

    private BomVersion editable() {
        BomVersion version = new BomVersion();
        version.setBomVersionId(20L); version.setBomId(10L); version.setBomCode("BOM-001");
        version.setProductItemId(1L); version.setVersionNo(1L);
        version.setBaseQty(BigDecimal.ONE); version.setProductUnitCode("EA");
        BomComponent row = new BomComponent();
        row.setBomComponentId(30L); row.setBomVersionId(20L); row.setMaterialItemId(2L);
        row.setLineNo(1L); row.setRequiredQty(new BigDecimal("2.000000")); row.setMaterialUnitCode("EA");
        version.setComponents(new ArrayList<>(List.of(row)));
        when(mapper.findVersion(20L)).thenReturn(version);
        when(mapper.findVersionForUpdate(20L)).thenReturn(version);
        when(mapper.findBomForUpdate(10L)).thenReturn(header());
        when(mapper.findComponents(20L)).thenAnswer(call -> version.getComponents());
        return version;
    }

    private Bom header() {
        Bom bom = new Bom(); bom.setBomId(10L); bom.setBomCode("BOM-001"); bom.setProductItemId(1L);
        return bom;
    }

    private Item item(Long id, String kind, String unit, int active) {
        Item item = new Item(); item.setItemId(id); item.setItemKind(kind);
        item.setUnitCode(unit); item.setIsActive(active); return item;
    }

    private BomForm form() {
        BomForm form = new BomForm(); form.setBomCode("BOM-001");
        form.setProductItemId(1L); form.setVersionNo(1L);
        form.setComponents(new ArrayList<>(List.of(componentForm(null, 2L, "2"))));
        return form;
    }

    private BomComponentForm componentForm(Long id, Long material, String qty) {
        BomComponentForm form = new BomComponentForm(); form.setBomComponentId(id);
        form.setMaterialItemId(material); form.setRequiredQty(new BigDecimal(qty)); return form;
    }
}
