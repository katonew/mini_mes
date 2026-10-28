package com.taehyung.mes.lot;

import com.taehyung.mes.common.web.PageExceptionHandler;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindingResult;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MaterialConsumptionControllerTests {
    private MaterialConsumptionService service;
    private MockMvc mvc;
    @BeforeEach void setUp() {
        service = mock(MaterialConsumptionService.class);
        mvc = MockMvcBuilders.standaloneSetup(new MaterialConsumptionController(service)).setControllerAdvice(new PageExceptionHandler()).build();
    }
    @Test void newScreenCreatesUuidAndGetRefreshAndPostErrorKeepIt() throws Exception {
        var initial = mvc.perform(get("/material-consumptions/new")).andExpect(view().name("lot/consumption/form")).andReturn();
        var form = (MaterialConsumptionForm) initial.getModelAndView().getModel().get("materialConsumptionForm");
        String key = form.getRequestKey(); assertThat(UUID.fromString(key).toString()).isEqualTo(key);
        var refreshed = mvc.perform(get("/material-consumptions/new").param("requestKey", key).param("workOrderId", "1"))
                .andExpect(view().name("lot/consumption/form")).andReturn();
        assertThat(((MaterialConsumptionForm) refreshed.getModelAndView().getModel().get("materialConsumptionForm")).getRequestKey()).isEqualTo(key);
        var failed = mvc.perform(post("/material-consumptions").param("requestKey", key).param("consumedQty", "bad"))
                .andExpect(model().attributeHasFieldErrors("materialConsumptionForm", "consumedQty")).andReturn();
        assertThat(((MaterialConsumptionForm) failed.getModelAndView().getModel().get("materialConsumptionForm")).getRequestKey()).isEqualTo(key);
        verify(service, never()).create(any());
    }
    @Test void storageErrorsKeepPayloadAndSuppressAllServerManagedValues() throws Exception {
        doThrow(new CannotAcquireLockException("lock wait")).when(service).create(any());
        String key = UUID.randomUUID().toString();
        var failed = mvc.perform(post("/material-consumptions").param("requestKey", key).param("workOrderId", "1")
                        .param("bomComponentId", "2").param("rawLotId", "3").param("consumedQty", "1.000000")
                        .param("consumptionId", "99").param("receiptId", "99").param("consumedAt", "2000-01-01T00:00")
                        .param("createdBy", "INJECTED").param("availableBefore", "999").param("payloadFingerprint", "INJECTED"))
                .andExpect(view().name("lot/consumption/form")).andExpect(model().attributeHasErrors("materialConsumptionForm")).andReturn();
        var model = failed.getModelAndView().getModel(); var form = (MaterialConsumptionForm) model.get("materialConsumptionForm");
        assertThat(form.getRequestKey()).isEqualTo(key); assertThat(form.getRawLotId()).isEqualTo(3L); assertThat(form.getConsumedQty()).isEqualByComparingTo("1");
        var binding = (BindingResult) model.get(BindingResult.MODEL_KEY_PREFIX + "materialConsumptionForm");
        assertThat(binding.getSuppressedFields()).contains("consumptionId", "receiptId", "consumedAt", "createdBy", "availableBefore", "payloadFingerprint");
    }
    @Test void successRedirectsToTheExistingConsumptionAndNoMutationEndpointExists() throws Exception {
        var outcome = new MaterialConsumptionOutcome(1,7L,1L,2L,3L,"1","EA",java.time.LocalDateTime.of(2026,10,27,0,0),"10","9");
        when(service.create(any())).thenReturn(outcome);
        mvc.perform(post("/material-consumptions")).andExpect(redirectedUrl("/material-consumptions/7")).andExpect(flash().attributeExists("successMessage"));
        mvc.perform(post("/material-consumptions/7")).andExpect(status().isMethodNotAllowed());
        when(service.get(7L)).thenReturn(new MaterialConsumption());
        mvc.perform(get("/material-consumptions/7")).andExpect(view().name("lot/consumption/detail"));
    }
    @Test void listSearchAndSafeNotFoundDatabaseAndRuleMessages() throws Exception {
        when(service.list("1", "LOT", "자재")).thenReturn(List.of());
        mvc.perform(get("/material-consumptions").param("workOrderId", "1").param("lotNo", "LOT").param("keyword", "자재"))
                .andExpect(view().name("lot/consumption/list")).andExpect(model().attribute("activeMenu", "material-consumptions"));
        when(service.get(99L)).thenThrow(new MaterialConsumptionNotFoundException());
        mvc.perform(get("/material-consumptions/99")).andExpect(status().isNotFound()).andExpect(model().attribute("activeMenu", "material-consumptions"));
        when(service.list("", "", "")).thenThrow(new CannotAcquireLockException("private"));
        mvc.perform(get("/material-consumptions")).andExpect(status().isServiceUnavailable());
        doThrow(new MaterialConsumptionRuleException("requestKey", "다른 payload")).when(service).create(any());
        mvc.perform(post("/material-consumptions")).andExpect(model().attributeHasFieldErrors("materialConsumptionForm", "requestKey"));
    }
}
