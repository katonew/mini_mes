package com.taehyung.mes.lot;

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
public class MaterialReturnController {
    private static final Logger log=LoggerFactory.getLogger(MaterialReturnController.class);
    private final MaterialReturnService service;
    public MaterialReturnController(MaterialReturnService service) { this.service=service; }
    @InitBinder("materialReturnForm")
    void bindForm(WebDataBinder binder) { binder.setAllowedFields("consumptionId","returnedQty","reason","requestKey"); }
    @ModelAttribute("activeMenu") String activeMenu() { return "material-returns"; }
    @GetMapping("/material-returns")
    public String list(@RequestParam(name="workOrderId",defaultValue="") String workOrderId,
                       @RequestParam(name="consumptionId",defaultValue="") String consumptionId,
                       @RequestParam(name="lotNo",defaultValue="") String lotNo,
                       @RequestParam(name="keyword",defaultValue="") String keyword,Model model) {
        model.addAttribute("workOrderId",workOrderId); model.addAttribute("consumptionId",consumptionId); model.addAttribute("lotNo",lotNo); model.addAttribute("keyword",keyword);
        try { model.addAttribute("returns",service.list(workOrderId,consumptionId,lotNo,keyword)); }
        catch (MaterialReturnRuleException exception) { model.addAttribute("returns",List.of()); model.addAttribute("searchError",exception.getMessage()); }
        return "lot/return/list";
    }
    @GetMapping("/material-returns/new")
    public String newForm(@RequestParam(name="consumptionId",required=false) Long consumptionId,Model model) {
        var form=new MaterialReturnForm(); form.setConsumptionId(consumptionId); form.setRequestKey(UUID.randomUUID().toString());
        model.addAttribute("materialReturnForm",form); return formView(model,form);
    }
    @PostMapping("/material-returns")
    public String create(@ModelAttribute("materialReturnForm") MaterialReturnForm form,BindingResult errors,Model model,RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try { var outcome=service.create(form); redirect.addFlashAttribute("successMessage","원자재 반환 처리 결과입니다."); return "redirect:/material-returns/"+outcome.returnId(); }
            catch (MaterialReturnRuleException exception) {
                if (exception.getField()==null) { errors.reject("materialReturn.rule",exception.getMessage()); }
                else { errors.rejectValue(exception.getField(),"materialReturn.rule",exception.getMessage()); }
            } catch (DataAccessException exception) {
                log.warn("원자재 반환 저장 결과 확인 실패: {}",exception.getClass().getSimpleName());
                errors.reject("materialReturn.storage","처리 결과를 확인하지 못했습니다. 요청 키와 입력값은 유지됩니다. 반환 이력 조회 후 같은 요청 키·같은 입력으로 재시도해 주세요.");
            }
        }
        return formView(model,form);
    }
    @GetMapping("/material-returns/{returnId}")
    public String detail(@PathVariable("returnId") Long returnId,Model model) { model.addAttribute("materialReturn",service.get(returnId)); return "lot/return/detail"; }
    private String formView(Model model,MaterialReturnForm form) {
        MaterialConsumption source=null;
        try { source=service.source(form.getConsumptionId()); }
        catch (MaterialReturnRuleException exception) { model.addAttribute("sourceError",exception.getMessage()); }
        model.addAttribute("source",source); return "lot/return/form";
    }
}
