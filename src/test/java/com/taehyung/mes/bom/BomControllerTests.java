package com.taehyung.mes.bom;

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

class BomControllerTests {
    private BomService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(BomService.class);
        mvc = MockMvcBuilders.standaloneSetup(new BomController(service))
                .setControllerAdvice(new PageExceptionHandler()).build();
    }

    @Test
    void successfulCreateRedirectsToVersion() throws Exception {
        when(service.create(any())).thenReturn(20L);
        mvc.perform(post("/boms").param("bomCode", "BOM-001").param("productItemId", "1")
                        .param("versionNo", "1").param("components[0].materialItemId", "2")
                        .param("components[0].requiredQty", "2"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/boms/versions/20"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void listAndDetailHaveModelsAndMissingVersionReturns404() throws Exception {
        when(service.list("", "")).thenReturn(List.of());
        mvc.perform(get("/boms")).andExpect(view().name("bom/list"))
                .andExpect(model().attribute("boms", List.of()));
        when(service.get(20L)).thenReturn(new BomVersion());
        mvc.perform(get("/boms/versions/20")).andExpect(view().name("bom/detail"))
                .andExpect(model().attributeExists("version"));
        when(service.get(99L)).thenThrow(new BomNotFoundException());
        mvc.perform(get("/boms/versions/99")).andExpect(status().isNotFound()).andExpect(view().name("error"));
    }

    @Test
    void bindingErrorsDoNotSaveAndPreserveInput() throws Exception {
        mvc.perform(post("/boms").param("bomCode", "BOM-001").param("versionNo", "abc")
                        .param("components[0].requiredQty", "wrong"))
                .andExpect(view().name("bom/form"))
                .andExpect(model().attributeHasFieldErrors("bomForm", "versionNo", "components[0].requiredQty"));
        verify(service, never()).create(any());
    }

    @Test
    void businessAndStorageErrorsRemainOnForm() throws Exception {
        when(service.create(any())).thenThrow(new BomRuleException("bomCode", "중복 코드"));
        mvc.perform(post("/boms").param("bomCode", "BOM-001"))
                .andExpect(view().name("bom/form")).andExpect(model().attributeHasFieldErrors("bomForm", "bomCode"));
        when(service.create(any())).thenThrow(new DataAccessResourceFailureException("private connection"));
        mvc.perform(post("/boms").param("bomCode", "BOM-001"))
                .andExpect(view().name("bom/form")).andExpect(model().attributeHasErrors("bomForm"));
    }

    @Test
    void createsNewVersionAndUpdatesExistingVersion() throws Exception {
        when(service.addVersion(eq(10L), any())).thenReturn(21L);
        mvc.perform(post("/boms/10/versions").param("versionNo", "2"))
                .andExpect(redirectedUrl("/boms/versions/21"));
        mvc.perform(post("/boms/versions/20").param("versionNo", "1"))
                .andExpect(redirectedUrl("/boms/versions/20"));
        verify(service).update(eq(20L), any());
    }

    @Test
    void usedVersionUpdateReturnsBusinessError() throws Exception {
        doThrow(new BomRuleException(null, "사용 이력이 있어 수정할 수 없습니다."))
                .when(service).update(eq(20L), any());
        mvc.perform(post("/boms/versions/20")).andExpect(view().name("bom/form"))
                .andExpect(model().attributeHasErrors("bomForm"));
    }

    @Test
    void databaseReadFailureReturns503() throws Exception {
        when(service.list("", "")).thenThrow(new DataAccessResourceFailureException("private"));
        mvc.perform(get("/boms")).andExpect(status().isServiceUnavailable()).andExpect(view().name("error"));
    }

    @Test
    void exposesCreateVersionAndEditFormsWithoutAllowingUsedEdit() throws Exception {
        mvc.perform(get("/boms/new")).andExpect(view().name("bom/form"))
                .andExpect(model().attribute("mode", "create"));
        Bom header = new Bom(); header.setBomId(10L); header.setBomCode("BOM-001"); header.setProductItemId(1L);
        when(service.getHeader(10L)).thenReturn(header);
        mvc.perform(get("/boms/10/versions/new")).andExpect(view().name("bom/form"))
                .andExpect(model().attribute("mode", "version"));
        BomVersion version = new BomVersion();
        version.setBomVersionId(20L); version.setBomId(10L);
        when(service.get(20L)).thenReturn(version);
        mvc.perform(get("/boms/versions/20/edit")).andExpect(view().name("bom/form"))
                .andExpect(model().attribute("mode", "edit"));
        version.setWorkOrderReferenced(true);
        mvc.perform(get("/boms/versions/20/edit")).andExpect(view().name("bom/detail"))
                .andExpect(model().attributeExists("notice"));
    }

    @Test
    void forgedActorUnitAndUsageMarkerAreNotBound() throws Exception {
        when(service.create(any())).thenReturn(20L);
        mvc.perform(post("/boms").param("bomCode", "BOM-001")
                        .param("createdBy", "FORGED").param("baseQty", "99")
                        .param("firstUsedAt", "2026-01-01T00:00:00").param("productUnitCode", "G"))
                .andExpect(redirectedUrl("/boms/versions/20"));
        verify(service).create(argThat(form -> "BOM-001".equals(form.getBomCode())));
    }
}
