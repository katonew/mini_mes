package com.taehyung.mes.routing;

import com.taehyung.mes.common.web.PageExceptionHandler;
import java.time.LocalDateTime;
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

class RoutingControllerTests {
    private RoutingService service;
    private MockMvc mvc;
    @BeforeEach
    void setUp() {
        service = mock(RoutingService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RoutingController(service)).setControllerAdvice(new PageExceptionHandler()).build();
    }
    @Test
    void createAndRevisionAdditionRedirectToSavedDetail() throws Exception {
        when(service.create(any())).thenReturn(20L);
        mvc.perform(post("/routings").param("routingCode", "R-1").param("productItemId", "1").param("revisionNo", "1")
                        .param("steps[0].processId", "2").param("steps[0].stepNo", "10").param("steps[0].processNameSnapshot", "FORGED"))
                .andExpect(redirectedUrl("/routings/revisions/20")).andExpect(flash().attributeExists("successMessage"));
        when(service.addRevision(eq(10L), any())).thenReturn(21L);
        mvc.perform(post("/routings/10/revisions")).andExpect(redirectedUrl("/routings/revisions/21"));
    }
    @Test
    void bindingAndBusinessErrorsKeepFormWithoutSavingInvalidNumbers() throws Exception {
        mvc.perform(post("/routings").param("revisionNo", "bad").param("steps[0].stepNo", "bad"))
                .andExpect(view().name("routing/form")).andExpect(model().attributeHasFieldErrors("routingForm", "revisionNo", "steps[0].stepNo"));
        verify(service, never()).create(any());
        when(service.create(any())).thenThrow(new RoutingRuleException("revisionNo", "중복 개정"));
        mvc.perform(post("/routings")).andExpect(view().name("routing/form")).andExpect(model().attributeHasFieldErrors("routingForm", "revisionNo"));
    }
    @Test
    void listAndNewFormsUseRoutingMenu() throws Exception {
        when(service.list("FG", "제품")).thenReturn(List.of());
        mvc.perform(get("/routings").param("code", "FG").param("name", "제품"))
                .andExpect(view().name("routing/list")).andExpect(model().attribute("activeMenu", "routings"));
        mvc.perform(get("/routings/new")).andExpect(view().name("routing/form"));
        Routing header = new Routing(); header.setRoutingId(10L); header.setRoutingCode("R-1"); header.setProductItemId(1L);
        when(service.getHeader(10L)).thenReturn(header);
        mvc.perform(get("/routings/10/revisions/new")).andExpect(view().name("routing/form")).andExpect(model().attribute("addingRevision", true));
    }
    @Test
    void usedRevisionCanBeReadButHasNoUpdateOrDeleteEndpoint() throws Exception {
        RoutingRevision used = new RoutingRevision(); used.setFirstUsedAt(LocalDateTime.now()); used.setWorkOrderReferenced(true);
        when(service.get(20L)).thenReturn(used);
        mvc.perform(get("/routings/revisions/20")).andExpect(view().name("routing/detail")).andExpect(model().attribute("revision", used));
        mvc.perform(post("/routings/revisions/20").param("steps[0].stepNo", "5")).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/routings/revisions/20")).andExpect(status().isMethodNotAllowed());
        verify(service, never()).create(any()); verify(service, never()).addRevision(any(), any());
    }
    @Test
    void missingRevisionAndDatabaseFailureUseSafeRoutingErrorPage() throws Exception {
        when(service.get(99L)).thenThrow(new RoutingNotFoundException());
        mvc.perform(get("/routings/revisions/99")).andExpect(status().isNotFound()).andExpect(model().attribute("activeMenu", "routings"));
        when(service.list("", "")).thenThrow(new DataAccessResourceFailureException("private"));
        mvc.perform(get("/routings")).andExpect(status().isServiceUnavailable()).andExpect(model().attribute("activeMenu", "routings"));
        when(service.create(any())).thenThrow(new DataAccessResourceFailureException("private"));
        mvc.perform(post("/routings")).andExpect(view().name("routing/form")).andExpect(model().attributeHasErrors("routingForm"));
    }
}
