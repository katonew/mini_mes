package com.taehyung.mes.production;

import java.util.List;
import java.util.UUID;
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
public class ProductionResultController {
    private static final Logger log = LoggerFactory.getLogger(ProductionResultController.class);
    private final ProductionResultService service;
    public ProductionResultController(ProductionResultService service) { this.service = service; }
    @InitBinder("productionResultForm")
    void bindForm(WebDataBinder binder) { binder.setAllowedFields("workOrderId", "lotNo", "producedQty", "goodQty", "defectQty", "workerRef", "requestKey"); }
    @InitBinder("correctionForm")
    void bindCorrection(WebDataBinder binder) { binder.setAllowedFields("expectedRevision", "producedQty", "goodQty", "defectQty", "reason"); }
    @ModelAttribute("activeMenu") String activeMenu() { return "production-results"; }

    @GetMapping("/production-results")
    public String list(@RequestParam(name="workOrderId", defaultValue="") String workOrderId,
                       @RequestParam(name="lotNo", defaultValue="") String lotNo,
                       @RequestParam(name="keyword", defaultValue="") String keyword, Model model) {
        model.addAttribute("workOrderId", workOrderId); model.addAttribute("lotNo", lotNo); model.addAttribute("keyword", keyword);
        try { model.addAttribute("results", service.list(workOrderId, lotNo, keyword)); }
        catch (ProductionResultRuleException exception) { model.addAttribute("results", List.of()); model.addAttribute("searchError", exception.getMessage()); }
        return "production/result/list";
    }
    @GetMapping("/production-results/new")
    public String newForm(@RequestParam(name="workOrderId", required=false) Long workOrderId, Model model) {
        var form = new ProductionResultForm(); form.setWorkOrderId(workOrderId); form.setRequestKey(UUID.randomUUID().toString());
        model.addAttribute("productionResultForm", form); return formView(model, form);
    }
    @PostMapping("/production-results")
    public String create(@ModelAttribute("productionResultForm") ProductionResultForm form, BindingResult errors, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                var outcome = service.create(form);
                redirect.addFlashAttribute("successMessage", "생산실적 요청을 처리했습니다. 같은 요청의 재전송은 최초 결과를 표시합니다.");
                return "redirect:/production-results/" + outcome.productionResultId();
            } catch (ProductionResultRuleException exception) {
                if (exception.getField() == null) { errors.reject("productionResult.rule", exception.getMessage()); }
                else { errors.rejectValue(exception.getField(), "productionResult.rule", exception.getMessage()); }
            } catch (DataAccessException exception) {
                log.warn("생산실적 저장 실패: {}", exception.getClass().getSimpleName());
                errors.reject("productionResult.storage", "처리 결과를 확인하지 못했습니다. 이력을 확인하고 같은 요청 키·입력으로 재시도해 주세요.");
            }
        }
        return formView(model, form);
    }
    @GetMapping("/production-results/{resultId}")
    public String detail(@PathVariable("resultId") Long resultId, Model model) {
        correctionDetails(resultId, model); return "production/result/detail";
    }
    @GetMapping("/production-results/{resultId}/corrections")
    public String corrections(@PathVariable("resultId") Long resultId, Model model) {
        correctionDetails(resultId, model); return "production/result/correction-history";
    }
    @GetMapping("/production-results/{resultId}/corrections/new")
    public String correctionForm(@PathVariable("resultId") Long resultId, Model model) {
        var details = correctionDetails(resultId, model); var current = details.result();
        var form = new ProductionResultCorrectionForm(); form.setExpectedRevision(current.getRevisionNo());
        form.setProducedQty(current.getProducedQty()); form.setGoodQty(current.getGoodQty()); form.setDefectQty(current.getDefectQty());
        model.addAttribute("correctionForm", form); return "production/result/correction-form";
    }
    @PostMapping("/production-results/{resultId}/corrections")
    public String correct(@PathVariable("resultId") Long resultId, @ModelAttribute("correctionForm") ProductionResultCorrectionForm form,
                          BindingResult errors, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                service.correct(resultId, form); redirect.addFlashAttribute("successMessage", "생산수량을 정정하고 변경 전후 값과 정정 사유를 이력으로 저장했습니다.");
                return "redirect:/production-results/" + resultId;
            } catch (ProductionResultRuleException exception) {
                if (exception.getField() == null) { errors.reject("productionCorrection.rule", exception.getMessage()); }
                else { errors.rejectValue(exception.getField(), "productionCorrection.rule", exception.getMessage()); }
            } catch (DataAccessException exception) {
                log.warn("생산실적 정정 실패: {}", exception.getClass().getSimpleName());
                errors.reject("productionCorrection.storage", "정정 결과를 확인하지 못했습니다. 현재 실적과 정정 이력을 먼저 확인해 주세요. 입력 버전은 유지되며 자동 재시도하지 않습니다.");
            }
        }
        correctionDetails(resultId, model); return "production/result/correction-form";
    }
    private ProductionResultService.CorrectionDetails correctionDetails(Long resultId, Model model) {
        var details = service.correctionDetails(resultId);
        model.addAttribute("result", details.result()); model.addAttribute("order", details.order());
        model.addAttribute("totals", details.totals()); model.addAttribute("corrections", details.history());
        model.addAttribute("correctionAllowed", details.correctionAllowed()); return details;
    }
    private String formView(Model model, ProductionResultForm form) {
        model.addAttribute("orders", List.of()); model.addAttribute("order", null); model.addAttribute("lot", null);
        try {
            model.addAttribute("orders", service.openOrders());
            if (form.getWorkOrderId() != null) {
                model.addAttribute("order", service.order(form.getWorkOrderId()));
                model.addAttribute("lot", service.lotForOrder(form.getWorkOrderId()));
            }
        } catch (ProductionResultRuleException exception) { model.addAttribute("choicesError", exception.getMessage()); }
        catch (DataAccessException exception) {
            log.warn("생산실적 기준 조회 실패: {}", exception.getClass().getSimpleName());
            model.addAttribute("choicesError", "작업지시·LOT을 조회하지 못했습니다. 입력과 요청 키는 유지됩니다.");
        }
        return "production/result/form";
    }
}
