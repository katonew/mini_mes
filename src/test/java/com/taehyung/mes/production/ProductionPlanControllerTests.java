package com.taehyung.mes.production;

import com.taehyung.mes.common.web.PageExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductionPlanControllerTests {
    private ProductionPlanService service;
    private MockMvc mvc;
    @BeforeEach
    void setUp() {
        service = mock(ProductionPlanService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProductionPlanController(service)).setControllerAdvice(new PageExceptionHandler()).build();
    }
    @Test
    void successfulRegistrationRedirectsToDetail() throws Exception {
        when(service.create(any())).thenReturn(10L);
        mvc.perform(post("/production-plans").param("productItemId", "1").param("plannedQty", "100").param("plannedDate", "2026-11-01"))
                .andExpect(redirectedUrl("/production-plans/10")).andExpect(flash().attributeExists("successMessage"));
        verify(service).create(argThat(form -> form.getPlannedDate().toString().equals("2026-11-01")));
    }
    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "2026-13-01", "bad-date"})
    void invalidCalendarDateDoesNotReachService(String date) throws Exception {
        mvc.perform(post("/production-plans").param("productItemId", "1").param("plannedQty", "100").param("plannedDate", date))
                .andExpect(view().name("production/plan/form"))
                .andExpect(model().attributeHasFieldErrors("productionPlanForm", "plannedDate"));
        verify(service, never()).create(any());
    }
    @Test
    void missingDateGetsBusinessErrorAndKeepsForm() throws Exception {
        when(service.create(any())).thenThrow(new ProductionPlanRuleException("plannedDate", "계획일 필수"));
        mvc.perform(post("/production-plans").param("productItemId", "1").param("plannedQty", "100"))
                .andExpect(view().name("production/plan/form")).andExpect(model().attributeHasFieldErrors("productionPlanForm", "plannedDate"));
    }
    @Test
    void searchAndNewFormUseProductionPlanMenu() throws Exception {
        when(service.list("10", "FG", "제품")).thenReturn(List.of());
        mvc.perform(get("/production-plans").param("planId", "10").param("code", "FG").param("name", "제품"))
                .andExpect(view().name("production/plan/list")).andExpect(model().attribute("plans", List.of()))
                .andExpect(model().attribute("activeMenu", "production-plans"));
        mvc.perform(get("/production-plans/new")).andExpect(view().name("production/plan/form"));
    }
    @Test
    void referencedPlanCanBeViewedButCannotBeChangedOrDeleted() throws Exception {
        ProductionPlan referenced = new ProductionPlan(); referenced.setProductionPlanId(10L); referenced.setWorkOrderCount(2L);
        when(service.get(10L)).thenReturn(referenced);
        mvc.perform(get("/production-plans/10")).andExpect(view().name("production/plan/detail")).andExpect(model().attribute("plan", referenced));
        mvc.perform(post("/production-plans/10").param("plannedQty", "1")).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/production-plans/10")).andExpect(status().isMethodNotAllowed());
        verify(service, never()).create(any());
    }
    @Test
    void notFoundReadFailureAndWriteFailureHaveSafeErrors() throws Exception {
        when(service.get(99L)).thenThrow(new ProductionPlanNotFoundException());
        mvc.perform(get("/production-plans/99")).andExpect(status().isNotFound()).andExpect(model().attribute("activeMenu", "production-plans"));
        when(service.list("", "", "")).thenThrow(new DataAccessResourceFailureException("private connection"));
        mvc.perform(get("/production-plans")).andExpect(status().isServiceUnavailable()).andExpect(model().attribute("activeMenu", "production-plans"));
        when(service.create(any())).thenThrow(new DataAccessResourceFailureException("private connection"));
        mvc.perform(post("/production-plans").param("plannedQty", "100"))
                .andExpect(view().name("production/plan/form")).andExpect(model().attributeHasErrors("productionPlanForm"));
    }
    @Test
    void malformedQuantityKeepsBindingError() throws Exception {
        mvc.perform(post("/production-plans").param("plannedQty", "bad-number"))
                .andExpect(view().name("production/plan/form")).andExpect(model().attributeHasFieldErrors("productionPlanForm", "plannedQty"));
        verify(service, never()).create(any());
    }
}
