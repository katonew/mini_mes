package com.taehyung.mes.item;

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

class ItemControllerTests {
    private ItemService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ItemService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ItemController(service))
                .setControllerAdvice(new PageExceptionHandler()).build();
    }

    @Test
    void successfulCreateRedirectsInsteadOfResubmittingForm() throws Exception {
        when(service.create(any())).thenReturn(1L);
        mvc.perform(post("/items").param("itemCode", "SENSOR-001").param("itemName", "센서 칩")
                        .param("itemKind", "RAW").param("unitCode", "EA").param("isActive", "1"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/items"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void duplicateCodeKeepsInputAndFieldError() throws Exception {
        when(service.create(any())).thenThrow(new ItemRuleException("itemCode", "중복된 품목코드입니다."));
        mvc.perform(post("/items").param("itemCode", "SENSOR-001").param("itemName", "센서 칩")
                        .param("itemKind", "RAW").param("unitCode", "EA").param("isActive", "1"))
                .andExpect(status().isOk()).andExpect(view().name("item/form"))
                .andExpect(model().attributeHasFieldErrors("itemForm", "itemCode"))
                .andExpect(model().attribute("editing", false));
    }

    @Test
    void invalidActiveValueDoesNotCallService() throws Exception {
        mvc.perform(post("/items").param("isActive", "invalid"))
                .andExpect(view().name("item/form"))
                .andExpect(model().attributeHasFieldErrors("itemForm", "isActive"));
        verifyNoInteractions(service);
    }

    @Test
    void storageFailureKeepsFormWithSafeError() throws Exception {
        when(service.create(any())).thenThrow(new DataAccessResourceFailureException("private JDBC details"));
        mvc.perform(post("/items").param("itemCode", "SENSOR-001").param("itemName", "센서 칩")
                        .param("itemKind", "RAW").param("unitCode", "EA").param("isActive", "1"))
                .andExpect(view().name("item/form")).andExpect(model().attributeHasErrors("itemForm"));
    }

    @Test
    void missingItemReturnsNotFoundPage() throws Exception {
        when(service.get(99L)).thenThrow(new ItemNotFoundException());
        mvc.perform(get("/items/99/edit"))
                .andExpect(status().isNotFound()).andExpect(view().name("error"));
    }
}
