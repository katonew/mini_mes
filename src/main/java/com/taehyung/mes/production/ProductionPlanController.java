package com.taehyung.mes.production;

import com.taehyung.mes.item.Item;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProductionPlanController {
    private static final Logger log = LoggerFactory.getLogger(ProductionPlanController.class);
    private final ProductionPlanService service;
    public ProductionPlanController(ProductionPlanService service) { this.service = service; }
    @InitBinder("productionPlanForm")
    void bindForm(WebDataBinder binder) { binder.setAllowedFields("productItemId", "plannedQty", "plannedDate"); }
    @ModelAttribute("activeMenu")
    String activeMenu() { return "production-plans"; }

    @GetMapping("/production-plans")
    public String list(@RequestParam(name = "planId", defaultValue = "") String planId,
                       @RequestParam(name = "code", defaultValue = "") String code,
                       @RequestParam(name = "name", defaultValue = "") String name, Model model) {
        model.addAttribute("planId", planId); model.addAttribute("code", code); model.addAttribute("name", name);
        try { model.addAttribute("plans", service.list(planId, code, name)); }
        catch (ProductionPlanRuleException exception) { model.addAttribute("plans", List.of()); model.addAttribute("searchError", exception.getMessage()); }
        return "production/plan/list";
    }
    @GetMapping("/production-plans/new")
    public String newForm(Model model) {
        ProductionPlanForm form = new ProductionPlanForm(); model.addAttribute("productionPlanForm", form);
        return formView(model, form);
    }
    @GetMapping("/production-plans/{planId}")
    public String detail(@PathVariable("planId") Long planId, Model model) {
        model.addAttribute("plan", service.get(planId)); return "production/plan/detail";
    }
    @PostMapping("/production-plans")
    public String create(@ModelAttribute("productionPlanForm") ProductionPlanForm form, BindingResult errors,
                         Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                Long id = service.create(form); redirect.addFlashAttribute("successMessage", "생산계획을 등록했습니다.");
                return "redirect:/production-plans/" + id;
            } catch (ProductionPlanRuleException exception) {
                if (exception.getField() == null) { errors.reject("productionPlan.rule", exception.getMessage()); }
                else { errors.rejectValue(exception.getField(), "productionPlan.rule", exception.getMessage()); }
            } catch (DataAccessException exception) {
                log.warn("생산계획 저장 실패: {}", exception.getClass().getSimpleName());
                errors.reject("productionPlan.storage", "데이터베이스 저장에 실패했습니다. 입력값은 유지됩니다. 연결 상태를 확인한 후 다시 시도해 주세요.");
            }
        }
        return formView(model, form);
    }
    private String formView(Model model, ProductionPlanForm form) {
        List<Item> products = new ArrayList<>();
        try {
            products.addAll(service.products());
            Item selected = service.displayProduct(form.getProductItemId()); model.addAttribute("selectedProduct", selected);
            if (selected != null && products.stream().noneMatch(row -> Objects.equals(row.getItemId(), selected.getItemId()))) { products.add(selected); }
        } catch (DataAccessException exception) {
            log.warn("생산계획 품목 조회 실패: {}", exception.getClass().getSimpleName());
            model.addAttribute("choicesError", "품목 정보를 불러오지 못했습니다. 입력값은 유지됩니다. 연결 상태를 확인해 주세요.");
        }
        model.addAttribute("products", products); return "production/plan/form";
    }
}
