package com.taehyung.mes.trace;

import com.taehyung.mes.lot.RawMaterialLotNotFoundException;
import com.taehyung.mes.production.ProductionResultNotFoundException;
import java.util.List;
import com.taehyung.mes.common.web.PageExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LotTraceControllerTests {
    private LotTraceService service;
    private MockMvc mvc;
    @BeforeEach void setUp() {
        service = mock(LotTraceService.class);
        mvc = MockMvcBuilders.standaloneSetup(new LotTraceController(service))
                .setControllerAdvice(new PageExceptionHandler()).build();
    }
    @Test void searchKeepsGetConditionsAndTraceMenu() throws Exception {
        mvc.perform(get("/lot-traces").param("direction", "forward").param("lotNo", "RM-1"))
                .andExpect(status().isOk()).andExpect(view().name("trace/list"))
                .andExpect(model().attribute("direction", "forward"))
                .andExpect(model().attribute("lotNo", "RM-1"))
                .andExpect(model().attribute("activeMenu", "lot-traces"));
    }
    @Test void invalidDirectionAndLongSearchAreRejectedBeforeQuery() throws Exception {
        mvc.perform(get("/lot-traces").param("direction", "other"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("searchError"));
        mvc.perform(get("/lot-traces").param("lotNo", "X".repeat(65)))
                .andExpect(status().isOk()).andExpect(model().attributeExists("searchError"));
        verifyNoInteractions(service);
    }
    @Test void originalBackwardUrlIsPreserved() throws Exception {
        when(service.get(7L)).thenReturn(new LotTraceDetails());
        mvc.perform(get("/finished-goods-lots/7/trace"))
                .andExpect(status().isOk()).andExpect(view().name("trace/detail"));
        verify(service).get(7L);
    }
    @Test void backwardSearchKeepsConditionsAndUsesFinishedLots() throws Exception {
        when(service.searchFinished("FG-1")).thenReturn(List.of());
        mvc.perform(get("/lot-traces").param("direction", "backward").param("lotNo", "FG-1"))
                .andExpect(status().isOk()).andExpect(model().attribute("direction", "backward"))
                .andExpect(model().attribute("lotNo", "FG-1"));
        verify(service).searchFinished("FG-1"); verify(service, never()).searchRaw(anyString());
    }
    @Test void forwardRouteUsesDedicatedTraceFlow() throws Exception {
        mvc.perform(get("/raw-material-lots/3/trace"))
                .andExpect(status().isOk()).andExpect(view().name("trace/forward"));
        verify(service).forward(3L);
    }
    @Test void missingLotsReturn404AndKeepSearchDirection() throws Exception {
        when(service.forward(99L)).thenThrow(new RawMaterialLotNotFoundException());
        when(service.get(99L)).thenThrow(new ProductionResultNotFoundException());
        mvc.perform(get("/raw-material-lots/99/trace"))
                .andExpect(status().isNotFound()).andExpect(view().name("trace/list"))
                .andExpect(model().attribute("direction", "forward")).andExpect(model().attributeExists("searchError"));
        mvc.perform(get("/finished-goods-lots/99/trace"))
                .andExpect(status().isNotFound()).andExpect(view().name("trace/list"))
                .andExpect(model().attribute("direction", "backward")).andExpect(model().attributeExists("searchError"));
    }
}
