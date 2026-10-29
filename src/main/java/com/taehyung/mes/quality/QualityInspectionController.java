package com.taehyung.mes.quality;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class QualityInspectionController {
    private static final Logger log = LoggerFactory.getLogger(QualityInspectionController.class);
    private final QualityInspectionService service;
    public QualityInspectionController(QualityInspectionService service) { this.service = service; }
    @InitBinder("qualityInspectionForm")
    void bindForm(WebDataBinder binder) {
        binder.setAllowedFields("finishedLotId", "inspectedQty", "acceptedQty", "rejectedQty", "inspectorRef", "judgementReason", "requestKey");
    }
    @ModelAttribute("activeMenu") String activeMenu() { return "quality-inspections"; }
    @GetMapping("/quality-inspections")
    public String list(@RequestParam(name="workOrderId", defaultValue="") String workOrderId,
                       @RequestParam(name="finishedLotId", defaultValue="") String finishedLotId,
                       @RequestParam(name="lotNo", defaultValue="") String lotNo,
                       @RequestParam(name="keyword", defaultValue="") String keyword, Model model) {
        model.addAttribute("workOrderId", workOrderId); model.addAttribute("finishedLotId", finishedLotId);
        model.addAttribute("lotNo", lotNo); model.addAttribute("keyword", keyword);
        try { model.addAttribute("inspections", service.list(workOrderId, finishedLotId, lotNo, keyword)); }
        catch (QualityInspectionRuleException exception) { model.addAttribute("inspections", List.of()); model.addAttribute("searchError", exception.getMessage()); }
        return "quality/list";
    }
    @GetMapping("/quality-inspections/new")
    public String newForm(@RequestParam(name="finishedLotId", required=false) Long finishedLotId, Model model) {
        var form = new QualityInspectionForm(); form.setFinishedLotId(finishedLotId); form.setRequestKey(UUID.randomUUID().toString());
        model.addAttribute("qualityInspectionForm", form); return formView(model, form);
    }
    @PostMapping("/quality-inspections")
    public String create(@ModelAttribute("qualityInspectionForm") QualityInspectionForm form, BindingResult errors,
                         Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                var outcome = service.create(form);
                redirect.addFlashAttribute("successMessage", "품질검사 요청을 처리했습니다. 같은 요청의 재전송은 최초 결과를 표시합니다.");
                return "redirect:/quality-inspections/" + outcome.inspectionId();
            } catch (QualityInspectionRuleException exception) {
                if (exception.getField() == null) { errors.reject("qualityInspection.rule", exception.getMessage()); }
                else { errors.rejectValue(exception.getField(), "qualityInspection.rule", exception.getMessage()); }
            } catch (DataAccessException exception) {
                log.warn("품질검사 저장 실패: {}", exception.getClass().getSimpleName());
                model.addAttribute("retryAvailable", true);
                errors.reject("qualityInspection.storage", "처리 결과를 확인하지 못했습니다. 이력을 확인하고 같은 요청 키·입력으로 재시도해 주세요.");
            }
        }
        return formView(model, form);
    }
    @GetMapping("/quality-inspections/{inspectionId}")
    public String detail(@PathVariable("inspectionId") Long inspectionId, Model model) {
        var inspection = service.get(inspectionId); model.addAttribute("inspection", inspection);
        model.addAttribute("lot", service.lot(inspection.getFinishedLotId())); return "quality/detail";
    }
    @GetMapping("/finished-goods-lots/{lotId}/inspections")
    public String history(@PathVariable("lotId") Long lotId, Model model) {
        var history = service.history(lotId); model.addAttribute("lot", history.lot());
        model.addAttribute("inspections", history.inspections()); return "quality/history";
    }
    @ExceptionHandler(QualityInspectionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(Model model) {
        model.addAttribute("safeTitle", "품질검사 또는 완제품 LOT을 찾을 수 없습니다");
        model.addAttribute("safeMessage", "품질검사 목록에서 대상을 다시 선택해 주세요.");
        model.addAttribute("activeMenu", "quality-inspections"); return "error";
    }
    private String formView(Model model, QualityInspectionForm form) {
        model.addAttribute("lots", List.of()); model.addAttribute("lot", null);
        try {
            model.addAttribute("lots", service.eligibleLots());
            if (form.getFinishedLotId() != null) { model.addAttribute("lot", service.lot(form.getFinishedLotId())); }
        } catch (QualityInspectionNotFoundException exception) { model.addAttribute("choicesError", "선택한 완제품 LOT을 찾을 수 없습니다."); }
        catch (DataAccessException exception) {
            log.warn("검사 대상 조회 실패: {}", exception.getClass().getSimpleName());
            model.addAttribute("choicesError", "검사 대상을 조회하지 못했습니다. 입력과 요청 키는 유지됩니다.");
        }
        return "quality/form";
    }
}
