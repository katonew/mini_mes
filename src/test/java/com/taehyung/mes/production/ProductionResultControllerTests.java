package com.taehyung.mes.production;

import com.taehyung.mes.common.web.PageExceptionHandler;
import java.time.LocalDateTime;
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

class ProductionResultControllerTests {
    private ProductionResultService service;
    private MockMvc mvc;
    @BeforeEach void setUp() {
        service = mock(ProductionResultService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProductionResultController(service), new FinishedGoodsLotController(service))
                .setControllerAdvice(new PageExceptionHandler()).build();
    }
    @Test void newFormCreatesUuidAndBindingErrorPreservesInputWhileSuppressingManagedFields() throws Exception {
        var page = mvc.perform(get("/production-results/new")).andExpect(view().name("production/result/form")).andReturn();
        String key = ((ProductionResultForm) page.getModelAndView().getModel().get("productionResultForm")).getRequestKey();
        assertThat(UUID.fromString(key).toString()).isEqualTo(key);
        var failed = mvc.perform(post("/production-results").param("workOrderId", "1").param("lotNo", "FG-1")
                .param("producedQty", "bad").param("goodQty", "1").param("defectQty", "0")
                .param("workerRef", "worker").param("requestKey", key).param("producedAt", "2000-01-01T00:00")
                .param("createdBy", "INJECTED").param("finishedLotId", "88").param("receiptId", "88"))
                .andExpect(view().name("production/result/form"))
                .andExpect(model().attributeHasFieldErrors("productionResultForm", "producedQty")).andReturn();
        var model = failed.getModelAndView().getModel();
        var form = (ProductionResultForm) model.get("productionResultForm");
        assertThat(form.getRequestKey()).isEqualTo(key); assertThat(form.getLotNo()).isEqualTo("FG-1");
        var binding = (BindingResult) model.get(BindingResult.MODEL_KEY_PREFIX + "productionResultForm");
        assertThat(binding.getSuppressedFields()).contains("producedAt", "createdBy", "finishedLotId", "receiptId");
        verify(service, never()).create(any());
    }
    @Test void successAndRuleFailureUseTheSameRequestForm() throws Exception {
        when(service.create(any())).thenReturn(new ProductionResultOutcome(1, 9L, 1L, 2L, "FG-1", "1", "1", "0", "worker", LocalDateTime.now()));
        mvc.perform(post("/production-results")).andExpect(redirectedUrl("/production-results/9"));
        doThrow(new ProductionResultRuleException("lotNo", "중복 LOT")).when(service).create(any());
        mvc.perform(post("/production-results").param("requestKey", UUID.randomUUID().toString()).param("lotNo", "FG-1"))
                .andExpect(model().attributeHasFieldErrors("productionResultForm", "lotNo"));
    }
    @Test void detailAndLotLookupUseSafeErrors() throws Exception {
        when(service.correctionDetails(99L)).thenThrow(new ProductionResultNotFoundException());
        mvc.perform(get("/production-results/99")).andExpect(status().isNotFound())
                .andExpect(model().attribute("activeMenu", "production-results"));
        when(service.lot(99L)).thenThrow(new ProductionResultNotFoundException());
        mvc.perform(get("/finished-goods-lots/99")).andExpect(status().isNotFound())
                .andExpect(model().attribute("activeMenu", "finished-goods-lots"));
        when(service.list("", "", "")).thenThrow(new CannotAcquireLockException("private"));
        mvc.perform(get("/production-results")).andExpect(status().isServiceUnavailable());
    }
}
