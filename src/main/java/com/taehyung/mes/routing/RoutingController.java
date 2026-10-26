package com.taehyung.mes.routing;

import com.taehyung.mes.item.Item;
import com.taehyung.mes.process.ManufacturingProcess;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
public class RoutingController {
    private static final Logger log = LoggerFactory.getLogger(RoutingController.class);
    private final RoutingService service;
    public RoutingController(RoutingService service) { this.service = service; }

    @InitBinder("routingForm")
    void bindForm(WebDataBinder binder) {
        binder.setAllowedFields("routingCode", "productItemId", "revisionNo", "steps[*].processId", "steps[*].stepNo");
        binder.setAutoGrowCollectionLimit(RoutingService.MAX_STEPS);
    }
    @ModelAttribute("activeMenu")
    String activeMenu() { return "routings"; }

    @GetMapping("/routings")
    public String list(@RequestParam(name = "code", defaultValue = "") String code,
                       @RequestParam(name = "name", defaultValue = "") String name, Model model) {
        model.addAttribute("code", code); model.addAttribute("name", name);
        try { model.addAttribute("routings", service.list(code, name)); }
        catch (RoutingRuleException exception) { model.addAttribute("routings", List.of()); model.addAttribute("searchError", exception.getMessage()); }
        return "routing/list";
    }

    @GetMapping("/routings/new")
    public String newForm(Model model) {
        RoutingForm form = new RoutingForm(); RoutingStepForm row = new RoutingStepForm(); row.setStepNo(1L); form.getSteps().add(row);
        model.addAttribute("routingForm", form); return formView(model, form, null);
    }

    @GetMapping("/routings/{routingId}/revisions/new")
    public String newRevision(@PathVariable("routingId") Long routingId, Model model) {
        Routing routing = service.getHeader(routingId);
        RoutingForm form = new RoutingForm(); form.setRoutingCode(routing.getRoutingCode()); form.setProductItemId(routing.getProductItemId());
        RoutingStepForm row = new RoutingStepForm(); row.setStepNo(1L); form.getSteps().add(row);
        model.addAttribute("routingForm", form); return formView(model, form, routingId);
    }

    @GetMapping("/routings/revisions/{revisionId}")
    public String detail(@PathVariable("revisionId") Long revisionId, Model model) {
        model.addAttribute("revision", service.get(revisionId)); return "routing/detail";
    }

    @PostMapping("/routings")
    public String create(@ModelAttribute("routingForm") RoutingForm form, BindingResult errors, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try { return saved(service.create(form), redirect); }
            catch (RoutingRuleException exception) { rejectRule(errors, exception); }
            catch (DataAccessException exception) { rejectStorage(errors, exception); }
        }
        return formView(model, form, null);
    }

    @PostMapping("/routings/{routingId}/revisions")
    public String addRevision(@PathVariable("routingId") Long routingId, @ModelAttribute("routingForm") RoutingForm form,
                              BindingResult errors, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try { return saved(service.addRevision(routingId, form), redirect); }
            catch (RoutingRuleException exception) { rejectRule(errors, exception); }
            catch (DataAccessException exception) { rejectStorage(errors, exception); }
        }
        return formView(model, form, routingId);
    }

    private String formView(Model model, RoutingForm form, Long routingId) {
        model.addAttribute("routingId", routingId); model.addAttribute("addingRevision", routingId != null);
        model.addAttribute("maxSteps", RoutingService.MAX_STEPS);
        List<Item> products = new ArrayList<>();
        Map<Long, ManufacturingProcess> choices = new LinkedHashMap<>();
        try {
            products.addAll(service.products());
            Item selected = service.displayProduct(form.getProductItemId());
            model.addAttribute("selectedProduct", selected);
            if (selected != null && products.stream().noneMatch(item -> Objects.equals(item.getItemId(), selected.getItemId()))) { products.add(selected); }
            service.processes().forEach(process -> choices.put(process.getProcessId(), process));
            if (form.getSteps() != null) {
                List<Long> ids = form.getSteps().stream().filter(Objects::nonNull).map(RoutingStepForm::getProcessId).filter(Objects::nonNull).distinct().toList();
                service.displayProcesses(ids).forEach(process -> choices.put(process.getProcessId(), process));
            }
        } catch (DataAccessException exception) {
            log.warn("Routing 선택 조회 실패: {}", exception.getClass().getSimpleName());
            model.addAttribute("choicesError", "선택 정보를 불러오지 못했습니다. 입력값은 유지됩니다. 연결 상태를 확인해 주세요.");
        }
        model.addAttribute("products", products); model.addAttribute("processes", choices.values());
        model.addAttribute("processChoices", choices);
        return "routing/form";
    }

    private String saved(Long id, RedirectAttributes redirect) {
        redirect.addFlashAttribute("successMessage", "Routing 개정을 등록했습니다."); return "redirect:/routings/revisions/" + id;
    }
    private void rejectRule(BindingResult errors, RoutingRuleException exception) {
        if (exception.getField() == null) { errors.reject("routing.rule", exception.getMessage()); }
        else { errors.rejectValue(exception.getField(), "routing.rule", exception.getMessage()); }
    }
    private void rejectStorage(BindingResult errors, DataAccessException exception) {
        log.warn("Routing 저장 실패: {}", exception.getClass().getSimpleName());
        errors.reject("routing.storage", "데이터베이스 저장에 실패했습니다. 입력값은 유지됩니다. 연결 상태를 확인한 후 다시 시도해 주세요.");
    }
}
