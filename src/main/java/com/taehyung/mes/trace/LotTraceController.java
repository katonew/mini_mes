package com.taehyung.mes.trace;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class LotTraceController {
    private final LotTraceService service;
    public LotTraceController(LotTraceService service) { this.service = service; }
    @ModelAttribute("activeMenu") String activeMenu() { return "finished-goods-lots"; }
    @GetMapping("/finished-goods-lots/{lotId}/trace")
    public String detail(@PathVariable("lotId") Long lotId, Model model) {
        model.addAttribute("trace", service.get(lotId)); return "trace/detail";
    }
}
