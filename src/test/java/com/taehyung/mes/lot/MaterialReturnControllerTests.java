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

class MaterialReturnControllerTests {
    private MaterialReturnService service;
    private MockMvc mvc;
    @BeforeEach void setUp() {
        service=mock(MaterialReturnService.class); mvc=MockMvcBuilders.standaloneSetup(new MaterialReturnController(service)).setControllerAdvice(new PageExceptionHandler()).build();
    }
    @Test void newScreenCreatesUuidAndErrorsKeepSourcePayloadAndKey() throws Exception {
        var initial=mvc.perform(get("/material-returns/new").param("consumptionId","7")).andExpect(view().name("lot/return/form")).andReturn();
        var form=(MaterialReturnForm)initial.getModelAndView().getModel().get("materialReturnForm"); String key=form.getRequestKey();
        assertThat(UUID.fromString(key).toString()).isEqualTo(key); assertThat(form.getConsumptionId()).isEqualTo(7L);
        var failed=mvc.perform(post("/material-returns").param("consumptionId","7").param("returnedQty","bad").param("reason","미사용").param("requestKey",key))
                .andExpect(model().attributeHasFieldErrors("materialReturnForm","returnedQty")).andReturn();
        form=(MaterialReturnForm)failed.getModelAndView().getModel().get("materialReturnForm"); assertThat(form.getRequestKey()).isEqualTo(key); assertThat(form.getReason()).isEqualTo("미사용");
        verify(service,never()).create(any());
    }
    @Test void forgedWorkOrderLotAndServerValuesCannotChangeTheReturnTarget() throws Exception {
        doThrow(new CannotAcquireLockException("lock wait")).when(service).create(any()); String key=UUID.randomUUID().toString();
        var result=mvc.perform(post("/material-returns").param("consumptionId","7").param("returnedQty","1.000000").param("reason","미사용").param("requestKey",key)
                .param("workOrderId","999").param("rawLotId","999").param("itemId","999").param("unitCode","M").param("receiptId","999").param("createdBy","INJECTED"))
                .andExpect(view().name("lot/return/form")).andExpect(model().attributeHasErrors("materialReturnForm")).andReturn();
        var form=(MaterialReturnForm)result.getModelAndView().getModel().get("materialReturnForm"); assertThat(form.getConsumptionId()).isEqualTo(7L); assertThat(form.getRequestKey()).isEqualTo(key);
        var binding=(BindingResult)result.getModelAndView().getModel().get(BindingResult.MODEL_KEY_PREFIX+"materialReturnForm");
        assertThat(binding.getSuppressedFields()).contains("workOrderId","rawLotId","itemId","unitCode","receiptId","createdBy");
    }
    @Test void successfulReturnRedirectsAndCompletedHistoryCanBeViewed() throws Exception {
        when(service.create(any())).thenReturn(new MaterialReturnOutcome(1,9L,7L,1L,3L,"1","EA","미사용",java.time.LocalDateTime.of(2026,10,28,0,0),"40","41"));
        mvc.perform(post("/material-returns")).andExpect(redirectedUrl("/material-returns/9"));
        var row=new MaterialReturn(); row.setWorkOrderStatus("COMPLETED"); when(service.get(9L)).thenReturn(row);
        mvc.perform(get("/material-returns/9")).andExpect(view().name("lot/return/detail"));
        mvc.perform(post("/material-returns/9")).andExpect(status().isMethodNotAllowed());
    }
    @Test void searchSourceAndSafeErrors() throws Exception {
        when(service.list("1","7","LOT","자재")).thenReturn(List.of());
        mvc.perform(get("/material-returns").param("workOrderId","1").param("consumptionId","7").param("lotNo","LOT").param("keyword","자재"))
                .andExpect(view().name("lot/return/list")).andExpect(model().attribute("activeMenu","material-returns"));
        when(service.get(99L)).thenThrow(new MaterialReturnNotFoundException());
        mvc.perform(get("/material-returns/99")).andExpect(status().isNotFound()).andExpect(model().attribute("activeMenu","material-returns"));
        when(service.list("","","","")).thenThrow(new CannotAcquireLockException("private")); mvc.perform(get("/material-returns")).andExpect(status().isServiceUnavailable());
        doThrow(new MaterialReturnRuleException("requestKey","다른 payload")).when(service).create(any());
        mvc.perform(post("/material-returns")).andExpect(model().attributeHasFieldErrors("materialReturnForm","requestKey"));
    }
}
