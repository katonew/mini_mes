package com.taehyung.mes.dashboard;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service) { this.service = service; }
    @ModelAttribute("activeMenu") String activeMenu() { return "dashboard"; }
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        var details = service.get(); model.addAttribute("summary", details.summary());
        model.addAttribute("materialLots", details.materialLots()); model.addAttribute("recentOrders", details.recentOrders());
        model.addAttribute("recentResults", details.recentResults()); model.addAttribute("recentInspections", details.recentInspections());
        model.addAttribute("warnings", details.warnings()); return "dashboard";
    }
}
