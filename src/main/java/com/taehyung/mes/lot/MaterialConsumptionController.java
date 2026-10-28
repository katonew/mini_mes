package com.taehyung.mes.lot;

import com.taehyung.mes.workorder.WorkOrder;
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
public class MaterialConsumptionController {
    private static final Logger log = LoggerFactory.getLogger(MaterialConsumptionController.class);
    private final MaterialConsumptionService service;
    public MaterialConsumptionController(MaterialConsumptionService service) { this.service = service; }
    @InitBinder("materialConsumptionForm")
    void bindForm(WebDataBinder binder) { binder.setAllowedFields("workOrderId", "bomComponentId", "rawLotId", "consumedQty", "requestKey"); }
    @ModelAttribute("activeMenu") String activeMenu() { return "material-consumptions"; }

    @GetMapping("/material-consumptions")
    public String list(@RequestParam(name="workOrderId", defaultValue="") String workOrderId,
                       @RequestParam(name="lotNo", defaultValue="") String lotNo,
                       @RequestParam(name="keyword", defaultValue="") String keyword, Model model) {
        model.addAttribute("workOrderId", workOrderId); model.addAttribute("lotNo", lotNo); model.addAttribute("keyword", keyword);
        try { model.addAttribute("consumptions", service.list(workOrderId, lotNo, keyword)); }
        catch (MaterialConsumptionRuleException exception) { model.addAttribute("consumptions", List.of()); model.addAttribute("searchError", exception.getMessage()); }
        return "lot/consumption/list";
    }
    @GetMapping("/material-consumptions/new")
    public String newForm(@RequestParam(name="workOrderId", required=false) Long workOrderId,
                          @RequestParam(name="bomComponentId", required=false) Long componentId,
                          @RequestParam(name="requestKey", required=false) String requestKey, Model model) {
        var form = new MaterialConsumptionForm(); form.setWorkOrderId(workOrderId); form.setBomComponentId(componentId);
        form.setRequestKey(requestKey == null ? UUID.randomUUID().toString() : requestKey);
        model.addAttribute("materialConsumptionForm", form); return formView(model, form);
    }
    @PostMapping("/material-consumptions")
    public String create(@ModelAttribute("materialConsumptionForm") MaterialConsumptionForm form, BindingResult errors, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                MaterialConsumptionOutcome outcome = service.create(form);
                redirect.addFlashAttribute("successMessage", "원자재 투입 요청을 처리했습니다. 같은 요청의 재전송은 기존 소비 결과를 표시합니다.");
                return "redirect:/material-consumptions/" + outcome.consumptionId();
            } catch (MaterialConsumptionRuleException exception) {
                if (exception.getField() == null) { errors.reject("materialConsumption.rule", exception.getMessage()); }
                else { errors.rejectValue(exception.getField(), "materialConsumption.rule", exception.getMessage()); }
            } catch (DataAccessException exception) {
                log.warn("원자재 소비 저장 실패: {}", exception.getClass().getSimpleName());
                errors.reject("materialConsumption.storage", "처리 결과를 확인하지 못했습니다. 요청 키와 입력값은 유지됩니다. 이력 조회 후 같은 요청 키·같은 입력으로 재시도해 주세요.");
            }
        }
        return formView(model, form);
    }
    @GetMapping("/material-consumptions/{consumptionId}")
    public String detail(@PathVariable("consumptionId") Long consumptionId, Model model) {
        model.addAttribute("consumption", service.get(consumptionId)); return "lot/consumption/detail";
    }
    private String formView(Model model, MaterialConsumptionForm form) {
        List<WorkOrder> orders = List.of(); MaterialConsumptionOptions options = null;
        try {
            orders = service.orders();
            if (form.getWorkOrderId() != null) { options = service.options(form.getWorkOrderId(), form.getBomComponentId()); }
        } catch (MaterialConsumptionRuleException exception) { model.addAttribute("choicesError", exception.getMessage()); }
        catch (DataAccessException exception) {
            log.warn("원자재 소비 기준 조회 실패: {}", exception.getClass().getSimpleName());
            model.addAttribute("choicesError", "작업지시·BOM·LOT를 조회하지 못했습니다. 요청 키와 입력값은 유지됩니다.");
        }
        List<MaterialConsumptionMaterial> materials = options == null ? List.of() : options.getMaterials();
        List<RawMaterialLot> lots = options == null ? List.of() : options.getLots();
        model.addAttribute("orders", orders); model.addAttribute("options", options);
        model.addAttribute("orderMissing", form.getWorkOrderId() != null && orders.stream().noneMatch(row -> row.getWorkOrderId().equals(form.getWorkOrderId())));
        model.addAttribute("componentMissing", form.getBomComponentId() != null && materials.stream().noneMatch(row -> row.getBomComponentId().equals(form.getBomComponentId())));
        model.addAttribute("lotMissing", form.getRawLotId() != null && lots.stream().noneMatch(row -> row.getRawLotId().equals(form.getRawLotId())));
        if (options != null && form.getBomComponentId() != null && materials.stream().noneMatch(row -> row.getBomComponentId().equals(form.getBomComponentId()))) {
            model.addAttribute("choicesError", "선택한 작업지시에 속한 BOM 구성 원자재를 다시 선택해 주세요.");
        }
        return "lot/consumption/form";
    }
}
