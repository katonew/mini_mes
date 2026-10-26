package com.taehyung.mes.workorder;

import java.util.List;
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
public class WorkOrderController {
    private static final Logger log = LoggerFactory.getLogger(WorkOrderController.class);
    private final WorkOrderService service;
    public WorkOrderController(WorkOrderService service) { this.service = service; }
    @InitBinder("workOrderForm")
    void bindForm(WebDataBinder binder) { binder.setAllowedFields("productionPlanId", "productItemId", "bomVersionId", "routingRevisionId", "orderedQty"); }
    @InitBinder("transitionForm")
    void bindTransition(WebDataBinder binder) { binder.setAllowedFields("action"); }
    @ModelAttribute("activeMenu")
    String activeMenu() { return "work-orders"; }
    @ModelAttribute("statuses")
    WorkOrderStatus[] statuses() { return WorkOrderStatus.values(); }

    @GetMapping("/work-orders")
    public String list(@RequestParam(name="id", defaultValue="") String id, @RequestParam(name="planId", defaultValue="") String planId,
                       @RequestParam(name="keyword", defaultValue="") String keyword, @RequestParam(name="status", defaultValue="") String status, Model model) {
        model.addAttribute("id", id); model.addAttribute("planId", planId); model.addAttribute("keyword", keyword); model.addAttribute("status", status);
        try { model.addAttribute("orders", service.list(id, planId, keyword, status)); }
        catch (WorkOrderRuleException exception) { model.addAttribute("orders", List.of()); model.addAttribute("searchError", exception.getMessage()); }
        return "workorder/list";
    }
    @GetMapping("/work-orders/new")
    public String newForm(@RequestParam(name="planId", required=false) Long planId, Model model) {
        WorkOrderForm form = new WorkOrderForm(); form.setProductionPlanId(planId); model.addAttribute("workOrderForm", form);
        return formView(model, form, true);
    }
    @PostMapping("/work-orders")
    public String create(@ModelAttribute("workOrderForm") WorkOrderForm form, BindingResult errors, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                Long id = service.create(form); redirect.addFlashAttribute("successMessage", "작업지시를 등록하고 선택한 BOM·Routing 사용 이력을 보존했습니다.");
                return "redirect:/work-orders/" + id;
            } catch (WorkOrderRuleException exception) {
                if (exception.getField() == null) { errors.reject("workOrder.rule", exception.getMessage()); }
                else { errors.rejectValue(exception.getField(), "workOrder.rule", exception.getMessage()); }
            } catch (DataAccessException exception) {
                log.warn("작업지시 저장 실패: {}", exception.getClass().getSimpleName());
                errors.reject("workOrder.storage", "저장 결과를 확인하지 못했습니다. 입력값은 유지됩니다. 목록에서 등록 여부를 조회한 후 재시도해 주세요.");
            }
        }
        return formView(model, form, false);
    }
    @GetMapping("/work-orders/{id}")
    public String detail(@PathVariable("id") Long id, Model model) { model.addAttribute("details", service.get(id)); return "workorder/detail"; }

    @PostMapping("/work-orders/{id}/transitions")
    public String transition(@PathVariable("id") Long id, @ModelAttribute("transitionForm") WorkOrderTransitionForm form,
                             BindingResult errors, RedirectAttributes redirect) {
        if (errors.hasErrors()) { redirect.addFlashAttribute("actionError", "상태 전이 입력값을 확인해 주세요."); }
        else {
            try { service.transition(id, form); redirect.addFlashAttribute("successMessage", "작업지시 상태를 변경했습니다."); }
            catch (WorkOrderRuleException exception) { redirect.addFlashAttribute("actionError", exception.getMessage()); }
            catch (DataAccessException exception) {
                log.warn("작업지시 상태 변경 실패: {}", exception.getClass().getSimpleName());
                redirect.addFlashAttribute("actionError", "상태 변경에 실패했습니다. 현재 상태를 다시 확인해 주세요.");
            }
        }
        return "redirect:/work-orders/" + id;
    }
    private String formView(Model model, WorkOrderForm form, boolean initializing) {
        model.addAttribute("plans", List.of()); model.addAttribute("options", null);
        WorkOrderOptions selectedOptions = null;
        try {
            model.addAttribute("plans", service.plans());
            if (form.getProductionPlanId() != null) {
                WorkOrderOptions options = service.options(form.getProductionPlanId()); selectedOptions = options; model.addAttribute("options", options);
                if (initializing) { form.setProductItemId(options.getPlan().getProductItemId()); }
            }
        } catch (WorkOrderRuleException exception) { model.addAttribute("choiceError", exception.getMessage()); }
        catch (DataAccessException exception) {
            log.warn("작업지시 선택 조회 실패: {}", exception.getClass().getSimpleName());
            model.addAttribute("choiceError", "계획·버전 선택 정보를 조회하지 못했습니다. 입력은 유지됩니다. 연결 상태를 확인해 주세요.");
        }
        List<WorkOrderBomOption> visibleBoms = selectedOptions == null ? List.of() : selectedOptions.getBoms();
        List<WorkOrderRoutingOption> visibleRoutings = selectedOptions == null ? List.of() : selectedOptions.getRoutings();
        model.addAttribute("bomSelectionMissing", form.getBomVersionId() != null && visibleBoms.stream().noneMatch(row -> row.getBomVersionId().equals(form.getBomVersionId())));
        model.addAttribute("routingSelectionMissing", form.getRoutingRevisionId() != null && visibleRoutings.stream().noneMatch(row -> row.getRoutingRevisionId().equals(form.getRoutingRevisionId())));
        model.addAttribute("registrationAllowed", selectedOptions != null && selectedOptions.getProductActive());
        return "workorder/form";
    }
}
