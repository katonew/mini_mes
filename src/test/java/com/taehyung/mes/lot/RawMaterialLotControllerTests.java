package com.taehyung.mes.lot;

import com.taehyung.mes.common.web.PageExceptionHandler;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindingResult;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RawMaterialLotControllerTests {
    private RawMaterialLotService service;
    private MockMvc mvc;
    @BeforeEach void setUp() {
        service = mock(RawMaterialLotService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RawMaterialLotController(service)).setControllerAdvice(new PageExceptionHandler()).build();
    }
    @Test void listSearchNewAndDetailUseLotViews() throws Exception {
        when(service.list("L", "RM", "자재")).thenReturn(List.of());
        mvc.perform(get("/raw-material-lots").param("lotNo", "L").param("code", "RM").param("name", "자재"))
                .andExpect(view().name("lot/raw/list")).andExpect(model().attribute("activeMenu", "raw-material-lots"));
        verify(service).list("L", "RM", "자재");
        mvc.perform(get("/raw-material-lots/new")).andExpect(view().name("lot/raw/form"));
        when(service.get(9L)).thenReturn(new RawMaterialLot());
        mvc.perform(get("/raw-material-lots/9")).andExpect(view().name("lot/raw/detail"));
    }
    @Test void onlyThreeInputFieldsAreBoundAndServerValuesCannotBeSubmitted() throws Exception {
        when(service.create(any())).thenAnswer(call -> {
            RawMaterialLotForm form = call.getArgument(0);
            assertThat(form.getItemId()).isEqualTo(1L); assertThat(form.getLotNo()).isEqualTo("L-1");
            assertThat(form.getRegisteredQty()).isEqualByComparingTo(new BigDecimal("10")); return 9L;
        });
        mvc.perform(post("/raw-material-lots").param("itemId", "1").param("lotNo", "L-1").param("registeredQty", "10")
                        .param("registeredAt", "2000-01-01T00:00").param("createdAt", "2000-01-01T00:00")
                        .param("availableQty", "999").param("createdBy", "INJECTED").param("unitCode", "G").param("rawLotId", "99"))
                .andExpect(redirectedUrl("/raw-material-lots/9")).andExpect(flash().attributeExists("successMessage"));
        doThrow(new RawMaterialLotRuleException("lotNo", "중복")).when(service).create(any());
        var result = mvc.perform(post("/raw-material-lots").param("itemId", "1").param("lotNo", "L-1").param("registeredQty", "10")
                        .param("registeredAt", "2000-01-01T00:00").param("availableQty", "999").param("createdBy", "INJECTED"))
                .andExpect(view().name("lot/raw/form")).andReturn();
        var binding = (BindingResult) result.getModelAndView().getModel().get(BindingResult.MODEL_KEY_PREFIX + "rawMaterialLotForm");
        assertThat(binding.getSuppressedFields()).contains("registeredAt", "availableQty", "createdBy");
        mvc.perform(post("/raw-material-lots/9").param("registeredAt", "2000-01-01T00:00")).andExpect(status().isMethodNotAllowed());
    }
    @Test void bindingBusinessAndStorageFailuresPreserveTheForm() throws Exception {
        mvc.perform(post("/raw-material-lots").param("registeredQty", "bad"))
                .andExpect(view().name("lot/raw/form")).andExpect(model().attributeHasFieldErrors("rawMaterialLotForm", "registeredQty"));
        verify(service, never()).create(any());
        when(service.create(any())).thenThrow(new RawMaterialLotRuleException("lotNo", "중복 LOT 번호"));
        mvc.perform(post("/raw-material-lots").param("itemId", "99").param("lotNo", "KEEP").param("registeredQty", "2"))
                .andExpect(model().attributeHasFieldErrors("rawMaterialLotForm", "lotNo")).andExpect(model().attribute("selectionMissing", true));
        doThrow(new DataAccessResourceFailureException("write failure")).when(service).create(any());
        when(service.choices()).thenThrow(new DataAccessResourceFailureException("choices failure"));
        mvc.perform(post("/raw-material-lots").param("itemId", "99").param("lotNo", "KEEP").param("registeredQty", "2"))
                .andExpect(view().name("lot/raw/form")).andExpect(model().attributeExists("choicesError"))
                .andExpect(model().attributeHasErrors("rawMaterialLotForm"));
    }
    @Test void missingLotAndDatabaseFailuresHaveSafeResponses() throws Exception {
        when(service.get(99L)).thenThrow(new RawMaterialLotNotFoundException());
        mvc.perform(get("/raw-material-lots/99")).andExpect(status().isNotFound()).andExpect(model().attribute("activeMenu", "raw-material-lots"));
        when(service.list("", "", "")).thenThrow(new DataAccessResourceFailureException("private"));
        mvc.perform(get("/raw-material-lots")).andExpect(status().isServiceUnavailable()).andExpect(model().attribute("activeMenu", "raw-material-lots"));
        when(service.list("bad", "", "")).thenThrow(new RawMaterialLotRuleException(null, "검색 오류"));
        mvc.perform(get("/raw-material-lots").param("lotNo", "bad")).andExpect(model().attribute("searchError", "검색 오류"));
    }
}
