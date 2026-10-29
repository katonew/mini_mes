package com.taehyung.mes.production;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class FinishedGoodsLotController {
    private final ProductionResultService service;
    public FinishedGoodsLotController(ProductionResultService service) { this.service = service; }
    @ModelAttribute("activeMenu") String activeMenu() { return "finished-goods-lots"; }
    @GetMapping("/finished-goods-lots")
    public String list(@RequestParam(name="workOrderId", defaultValue="") String workOrderId,
                       @RequestParam(name="lotNo", defaultValue="") String lotNo,
                       @RequestParam(name="keyword", defaultValue="") String keyword, Model model) {
        model.addAttribute("workOrderId", workOrderId); model.addAttribute("lotNo", lotNo); model.addAttribute("keyword", keyword);
        try { model.addAttribute("lots", service.lots(workOrderId, lotNo, keyword)); }
        catch (ProductionResultRuleException exception) { model.addAttribute("lots", List.of()); model.addAttribute("searchError", exception.getMessage()); }
        return "production/finished-lot/list";
    }
    @GetMapping("/finished-goods-lots/{lotId}")
    public String detail(@PathVariable("lotId") Long lotId, Model model) {
        model.addAttribute("lot", service.lot(lotId)); model.addAttribute("results", service.resultsForLot(lotId));
        return "production/finished-lot/detail";
    }
}
