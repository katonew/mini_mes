package com.taehyung.mes.process;

import com.taehyung.mes.common.web.PageExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProcessControllerTests {
    private ProcessService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ProcessService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProcessController(service))
                .setControllerAdvice(new PageExceptionHandler()).build();
    }

    @Test
    void successfulCreateRedirectsInsteadOfResubmittingForm() throws Exception {
        when(service.create(any())).thenReturn(1L);
        mvc.perform(post("/processes").param("processCode", "ASSEMBLY").param("processName", "모듈 조립")
                        .param("description", "조립 설명").param("isActive", "1"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/processes"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void duplicateCodeKeepsInputAndFieldError() throws Exception {
        when(service.create(any())).thenThrow(new ProcessRuleException("processCode", "중복된 공정코드입니다."));
        mvc.perform(post("/processes").param("processCode", "ASSEMBLY").param("processName", "모듈 조립")
                        .param("description", "조립 설명").param("isActive", "1"))
                .andExpect(status().isOk()).andExpect(view().name("process/form"))
                .andExpect(model().attributeHasFieldErrors("processForm", "processCode"))
                .andExpect(model().attribute("editing", false));
    }

    @Test
    void invalidActiveValueDoesNotCallService() throws Exception {
        mvc.perform(post("/processes").param("isActive", "invalid"))
                .andExpect(view().name("process/form"))
                .andExpect(model().attributeHasFieldErrors("processForm", "isActive"));
        verifyNoInteractions(service);
    }

    @Test
    void storageFailureKeepsFormWithSafeError() throws Exception {
        when(service.create(any())).thenThrow(new DataAccessResourceFailureException("private JDBC details"));
        mvc.perform(post("/processes").param("processCode", "ASSEMBLY").param("processName", "모듈 조립")
                        .param("description", "조립 설명").param("isActive", "1"))
                .andExpect(view().name("process/form")).andExpect(model().attributeHasErrors("processForm"));
    }

    @Test
    void missingProcessReturnsNotFoundPage() throws Exception {
        when(service.get(99L)).thenThrow(new ProcessNotFoundException());
        mvc.perform(get("/processes/99/edit"))
                .andExpect(status().isNotFound()).andExpect(view().name("error"));
    }

    @Test
    void searchesAndExposesEmptyListAndCreateForm() throws Exception {
        when(service.list("ASS", "조립")).thenReturn(java.util.List.of());
        mvc.perform(get("/processes").param("code", "ASS").param("name", "조립"))
                .andExpect(view().name("process/list")).andExpect(model().attribute("processes", java.util.List.of()))
                .andExpect(model().attribute("activeMenu", "processes"));
        verify(service).list("ASS", "조립");
        mvc.perform(get("/processes/new")).andExpect(view().name("process/form"))
                .andExpect(model().attribute("editing", false));
    }

    @Test
    void editDisplaysCurrentValuesAndSuccessfulUpdateRedirects() throws Exception {
        ManufacturingProcess process = new ManufacturingProcess(); process.setProcessId(1L);
        process.setProcessCode("ASSEMBLY"); process.setProcessName("조립"); process.setDescription("설명"); process.setIsActive(1);
        when(service.get(1L)).thenReturn(process);
        mvc.perform(get("/processes/1/edit")).andExpect(view().name("process/form"))
                .andExpect(model().attribute("editing", true)).andExpect(model().attribute("processId", 1L));
        mvc.perform(post("/processes/1").param("processCode", "ASSEMBLY").param("processName", "조립 변경")
                        .param("description", "설명 변경").param("isActive", "0"))
                .andExpect(redirectedUrl("/processes")).andExpect(flash().attributeExists("successMessage"));
        verify(service).update(org.mockito.ArgumentMatchers.eq(1L), argThat(form -> form.getIsActive() == 0
                && "설명 변경".equals(form.getDescription())));
    }

    @Test
    void updateRuleErrorKeepsFormAndDatabaseReadFailureReturns503() throws Exception {
        doThrow(new ProcessRuleException("processCode", "코드 변경 불가")).when(service).update(org.mockito.ArgumentMatchers.eq(1L), any());
        mvc.perform(post("/processes/1").param("processCode", "OTHER"))
                .andExpect(view().name("process/form")).andExpect(model().attributeHasFieldErrors("processForm", "processCode"));
        when(service.list("", "")).thenThrow(new DataAccessResourceFailureException("private JDBC details"));
        mvc.perform(get("/processes")).andExpect(status().isServiceUnavailable()).andExpect(view().name("error"))
                .andExpect(model().attribute("activeMenu", "processes"));
    }
}
