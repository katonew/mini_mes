package com.taehyung.mes.process;

import com.taehyung.mes.common.actor.EnvironmentActorProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessServiceTests {
    private ProcessMapper mapper;
    private ProcessService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProcessMapper.class);
        service = new ProcessService(mapper, new EnvironmentActorProvider("LOCAL_DEV"));
    }

    @Test
    void createsWithGeneratedIdAndServerActor() {
        when(mapper.insert(any())).thenAnswer(call -> {
            ManufacturingProcess row = call.getArgument(0);
            assertThat(row.getCreatedBy()).isEqualTo("LOCAL_DEV");
            assertThat(row.getProcessCode()).isEqualTo("ASSEMBLY");
            assertThat(row.getDescription()).isEqualTo("조립 공정 설명");
            row.setProcessId(1L); return 1;
        });
        assertThat(service.create(form())).isEqualTo(1L);
    }

    @Test
    void duplicatePrecheckAndDatabaseRaceAreRejected() {
        when(mapper.existsByCode("ASSEMBLY")).thenReturn(true);
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(ProcessRuleException.class);
        verify(mapper, never()).insert(any());
        when(mapper.existsByCode("ASSEMBLY")).thenReturn(false);
        when(mapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(ProcessRuleException.class).hasMessageContaining("중복");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "한글코드", "A\nB"})
    void missingOrNonAsciiCodeIsRejected(String code) {
        ProcessForm form = form(); form.setProcessCode(code);
        assertThatThrownBy(() -> service.create(form)).isInstanceOf(ProcessRuleException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void requiredNameAndExplicitActiveValueAreValidated() {
        ProcessForm form = form(); form.setProcessName(" ");
        assertThatThrownBy(() -> service.create(form)).isInstanceOf(ProcessRuleException.class);
        form.setProcessName("조립"); form.setIsActive(null);
        assertThatThrownBy(() -> service.create(form)).isInstanceOf(ProcessRuleException.class);
        form.setIsActive(2);
        assertThatThrownBy(() -> service.create(form)).isInstanceOf(ProcessRuleException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void columnLengthsAreCheckedBeforeSaving() {
        ProcessForm form = form(); form.setProcessCode("A".repeat(51));
        assertThatThrownBy(() -> service.create(form)).isInstanceOf(ProcessRuleException.class);
        form.setProcessCode("ASSEMBLY"); form.setProcessName("가".repeat(201));
        assertThatThrownBy(() -> service.create(form)).isInstanceOf(ProcessRuleException.class);
        form.setProcessName("조립"); form.setDescription("가".repeat(501));
        assertThatThrownBy(() -> service.create(form)).isInstanceOf(ProcessRuleException.class);
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void missingActorBlocksNewRegistration(String actor) {
        service = new ProcessService(mapper, new EnvironmentActorProvider(actor));
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(ProcessRuleException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void tooLongActorBlocksRegistration() {
        service = new ProcessService(mapper, new EnvironmentActorProvider("A".repeat(65)));
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(ProcessRuleException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void codeCannotBeChangedEvenByForgedRequest() {
        when(mapper.findByIdForUpdate(1L)).thenReturn(existing());
        ProcessForm form = form(); form.setProcessCode("OTHER");
        assertThatThrownBy(() -> service.update(1L, form)).isInstanceOf(ProcessRuleException.class);
        verify(mapper, never()).updateEditable(any(), any(), any(), any());
    }

    @Test
    void changesNameDescriptionAndActiveWithoutReplacingIdentityOrActor() {
        service = new ProcessService(mapper, new EnvironmentActorProvider(""));
        ManufacturingProcess original = existing();
        when(mapper.findByIdForUpdate(1L)).thenReturn(original);
        when(mapper.updateEditable(1L, "조립 변경", null, 0)).thenReturn(1);
        ProcessForm form = form(); form.setProcessName("조립 변경"); form.setDescription(null); form.setIsActive(0);
        service.update(1L, form);
        verify(mapper).updateEditable(1L, "조립 변경", null, 0);
        assertThat(original.getProcessId()).isEqualTo(1L);
        assertThat(original.getProcessCode()).isEqualTo("ASSEMBLY");
        assertThat(original.getCreatedBy()).isEqualTo("ORIGINAL_ACTOR");
        verify(mapper, never()).insert(any());
    }

    @Test
    void searchesAndLoadsExistingProcess() {
        ManufacturingProcess row = existing();
        when(mapper.findAll("ASS", "조립")).thenReturn(List.of(row));
        when(mapper.findById(1L)).thenReturn(row);
        assertThat(service.list("ASS", "조립")).containsExactly(row);
        assertThat(service.get(1L)).isSameAs(row);
        assertThatThrownBy(() -> service.list("A".repeat(51), "")).isInstanceOf(ProcessRuleException.class);
    }

    @Test
    void missingProcessIsNeverCreatedByUpdate() {
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ProcessNotFoundException.class);
        assertThatThrownBy(() -> service.get(0L)).isInstanceOf(ProcessNotFoundException.class);
        assertThatThrownBy(() -> service.update(99L, form())).isInstanceOf(ProcessNotFoundException.class);
        verify(mapper, never()).insert(any());
    }

    @Test
    void failedWritesNeverReportSuccess() {
        when(mapper.insert(any())).thenReturn(0);
        assertThatThrownBy(() -> service.create(form())).isInstanceOf(DataIntegrityViolationException.class);
        when(mapper.findByIdForUpdate(1L)).thenReturn(existing());
        assertThatThrownBy(() -> service.update(1L, form())).isInstanceOf(DataIntegrityViolationException.class);
    }

    private ManufacturingProcess existing() {
        ManufacturingProcess row = new ManufacturingProcess(); row.setProcessId(1L);
        row.setProcessCode("ASSEMBLY"); row.setProcessName("조립"); row.setIsActive(1);
        row.setCreatedBy("ORIGINAL_ACTOR"); return row;
    }

    private ProcessForm form() {
        ProcessForm form = new ProcessForm(); form.setProcessCode("ASSEMBLY"); form.setProcessName("조립");
        form.setDescription("조립 공정 설명"); form.setIsActive(1); return form;
    }
}
