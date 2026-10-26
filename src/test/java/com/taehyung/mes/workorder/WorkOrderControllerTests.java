package com.taehyung.mes.workorder;

import com.taehyung.mes.common.web.PageExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WorkOrderControllerTests {
    private WorkOrderService service;
    private MockMvc mvc;
    @BeforeEach void setUp() { service = mock(WorkOrderService.class); mvc = MockMvcBuilders.standaloneSetup(new WorkOrderController(service)).setControllerAdvice(new PageExceptionHandler()).build(); }
    @Test void listAndNewFormUseWorkOrderViews() throws Exception {
        when(service.list("", "", "", "")).thenReturn(List.of());
        mvc.perform(get("/work-orders")).andExpect(view().name("workorder/list")).andExpect(model().attribute("activeMenu", "work-orders"));
        mvc.perform(get("/work-orders/new")).andExpect(view().name("workorder/form"));
    }
    @Test void validCreateRedirectsAndInvalidQuantityBindingKeepsForm() throws Exception {
        when(service.create(any())).thenReturn(9L);
        mvc.perform(post("/work-orders").param("productionPlanId", "1").param("productItemId", "2").param("bomVersionId", "4").param("routingRevisionId", "6").param("orderedQty", "20"))
                .andExpect(redirectedUrl("/work-orders/9")).andExpect(flash().attributeExists("successMessage"));
        clearInvocations(service);
        mvc.perform(post("/work-orders").param("orderedQty", "bad"))
                .andExpect(view().name("workorder/form")).andExpect(model().attributeHasFieldErrors("workOrderForm", "orderedQty"));
        verify(service, never()).create(any());
    }
    @Test void startRedirectsAndUnsupportedCompleteHasError() throws Exception {
        mvc.perform(post("/work-orders/9/transitions").param("action", "START")).andExpect(redirectedUrl("/work-orders/9"));
        doThrow(new WorkOrderRuleException(null, "완료 미지원")).when(service).transition(eq(9L), any());
        mvc.perform(post("/work-orders/9/transitions").param("action", "COMPLETE")).andExpect(flash().attribute("actionError", "완료 미지원"));
    }
    @Test void missingAndStorageErrorsHaveSafeResponses() throws Exception {
        when(service.get(99L)).thenThrow(new WorkOrderNotFoundException());
        mvc.perform(get("/work-orders/99")).andExpect(status().isNotFound()).andExpect(model().attribute("activeMenu", "work-orders"));
        when(service.list("", "", "", "")).thenThrow(new DataAccessResourceFailureException("private"));
        mvc.perform(get("/work-orders")).andExpect(status().isServiceUnavailable()).andExpect(model().attribute("activeMenu", "work-orders"));
        when(service.create(any())).thenThrow(new WorkOrderRuleException("bomVersionId", "제품 불일치"));
        mvc.perform(post("/work-orders")).andExpect(model().attributeHasFieldErrors("workOrderForm", "bomVersionId"));
    }
    @Test void storageAndChoiceFailureKeepSubmittedVersionIdsAndBlockSave() throws Exception {
        when(service.create(any())).thenThrow(new DataAccessResourceFailureException("write unavailable"));
        when(service.plans()).thenThrow(new DataAccessResourceFailureException("choices unavailable"));
        mvc.perform(post("/work-orders").param("productionPlanId", "1").param("productItemId", "2")
                        .param("bomVersionId", "4").param("routingRevisionId", "6").param("orderedQty", "20"))
                .andExpect(view().name("workorder/form")).andExpect(model().attribute("bomSelectionMissing", true))
                .andExpect(model().attribute("routingSelectionMissing", true)).andExpect(model().attribute("registrationAllowed", false));
    }
}
