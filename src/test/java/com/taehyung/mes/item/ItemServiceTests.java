package com.taehyung.mes.item;

import com.taehyung.mes.common.actor.EnvironmentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ItemServiceTests {
    private ItemMapper mapper;
    private ItemService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ItemMapper.class);
        service = new ItemService(mapper, new EnvironmentActorProvider("LOCAL_DEV"));
    }

    @Test
    void createsWithServerActorAndGeneratedId() {
        when(mapper.insert(any(Item.class))).thenAnswer(invocation -> {
            Item row = invocation.getArgument(0);
            assertThat(row.getCreatedBy()).isEqualTo("LOCAL_DEV");
            row.setItemId(1L);
            return 1;
        });
        assertThat(service.create(form())).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void missingActorRejectsBeforeDatabaseAccess(String actor) {
        service = new ItemService(mapper, new EnvironmentActorProvider(actor));
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(ItemRuleException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void tooLongActorRejectsBeforeDatabaseAccess() {
        service = new ItemService(mapper, new EnvironmentActorProvider("A".repeat(65)));
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(ItemRuleException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void semiCannotBeRegistered() {
        ItemForm form = form();
        form.setItemKind("SEMI");
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(ItemRuleException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void invalidUnitAndActiveValueAreRejected() {
        ItemForm form = form();
        form.setUnitCode("KG");
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(ItemRuleException.class);
        form.setUnitCode("EA");
        form.setIsActive(2);
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(ItemRuleException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void asciiCodeAndNameLengthAreValidated() {
        ItemForm form = form();
        form.setItemCode("센서-001");
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(ItemRuleException.class);
        form.setItemCode("A".repeat(51));
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(ItemRuleException.class);
        form.setItemCode("SENSOR-001");
        form.setItemName("가".repeat(201));
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(ItemRuleException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void duplicatePrecheckAndDatabaseRaceAreBothRejected() {
        when(mapper.existsByCode("SENSOR-001")).thenReturn(true);
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(ItemRuleException.class);
        verify(mapper, never()).insert(any());

        when(mapper.existsByCode("SENSOR-001")).thenReturn(false);
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));
        assertThatThrownBy(() -> service.create(form()))
                .isInstanceOf(ItemRuleException.class).hasMessageContaining("중복");
    }

    @Test
    void immutableFieldsCannotBeChangedByForgedRequests() {
        when(mapper.findByIdForUpdate(1L)).thenReturn(existing());
        ItemForm codeChanged = form();
        codeChanged.setItemCode("OTHER");
        assertThatThrownBy(() -> service.update(1L, codeChanged)).isInstanceOf(ItemRuleException.class);
        ItemForm kindChanged = form();
        kindChanged.setItemKind("FINISHED");
        assertThatThrownBy(() -> service.update(1L, kindChanged)).isInstanceOf(ItemRuleException.class);
        ItemForm unitChanged = form();
        unitChanged.setUnitCode("M");
        assertThatThrownBy(() -> service.update(1L, unitChanged)).isInstanceOf(ItemRuleException.class);
        verify(mapper, never()).updateEditable(any(), any(), any());
    }

    @Test
    void updatesOnlyNameAndActiveWithoutRequiringNewActor() {
        service = new ItemService(mapper, new EnvironmentActorProvider(""));
        when(mapper.findByIdForUpdate(1L)).thenReturn(existing());
        when(mapper.updateEditable(1L, "센서 칩 변경", 0)).thenReturn(1);
        ItemForm form = form();
        form.setItemName("센서 칩 변경");
        form.setIsActive(0);
        service.update(1L, form);
        verify(mapper).updateEditable(1L, "센서 칩 변경", 0);
        verify(mapper, never()).insert(any());
    }

    @Test
    void missingItemIsNotSilentlyCreated() {
        assertThatThrownBy(() -> service.update(99L, form())).isInstanceOf(ItemNotFoundException.class);
        verify(mapper, never()).updateEditable(any(), any(), any());
    }

    private Item existing() {
        Item item = new Item();
        item.setItemId(1L);
        item.setItemCode("SENSOR-001");
        item.setItemName("센서 칩");
        item.setItemKind("RAW");
        item.setUnitCode("EA");
        item.setIsActive(1);
        item.setCreatedBy("ORIGINAL_ACTOR");
        return item;
    }

    private ItemForm form() {
        ItemForm form = new ItemForm();
        form.setItemCode("SENSOR-001");
        form.setItemName("센서 칩");
        form.setItemKind("RAW");
        form.setUnitCode("EA");
        form.setIsActive(1);
        return form;
    }
}
