package com.taehyung.mes.lot;

import com.taehyung.mes.item.Item;
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
public class RawMaterialLotController {
    private static final Logger log = LoggerFactory.getLogger(RawMaterialLotController.class);
    private final RawMaterialLotService service;
    public RawMaterialLotController(RawMaterialLotService service) { this.service = service; }

    @InitBinder("rawMaterialLotForm")
    void bindForm(WebDataBinder binder) { binder.setAllowedFields("itemId", "lotNo", "registeredQty"); }
    @ModelAttribute("activeMenu")
    String activeMenu() { return "raw-material-lots"; }

    @GetMapping("/raw-material-lots")
    public String list(@RequestParam(name="lotNo", defaultValue="") String lotNo,
                       @RequestParam(name="code", defaultValue="") String code,
                       @RequestParam(name="name", defaultValue="") String name, Model model) {
        model.addAttribute("lotNo", lotNo); model.addAttribute("code", code); model.addAttribute("name", name);
        try { model.addAttribute("lots", service.list(lotNo, code, name)); }
        catch (RawMaterialLotRuleException exception) { model.addAttribute("lots", List.of()); model.addAttribute("searchError", exception.getMessage()); }
        return "lot/raw/list";
    }

    @GetMapping("/raw-material-lots/new")
    public String newForm(Model model) {
        var form = new RawMaterialLotForm(); model.addAttribute("rawMaterialLotForm", form); return formView(model, form);
    }

    @PostMapping("/raw-material-lots")
    public String create(@ModelAttribute("rawMaterialLotForm") RawMaterialLotForm form, BindingResult errors,
                         Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                Long id = service.create(form); redirect.addFlashAttribute("successMessage", "원자재 LOT을 등록했습니다.");
                return "redirect:/raw-material-lots/" + id;
            } catch (RawMaterialLotRuleException exception) {
                if (exception.getField() == null) { errors.reject("rawMaterialLot.rule", exception.getMessage()); }
                else { errors.rejectValue(exception.getField(), "rawMaterialLot.rule", exception.getMessage()); }
            } catch (DataAccessException exception) {
                log.warn("원자재 LOT 저장 실패: {}", exception.getClass().getSimpleName());
                errors.reject("rawMaterialLot.storage", "저장 결과를 확인하지 못했습니다. 입력값은 유지됩니다. LOT 번호로 목록에서 등록 여부를 확인한 후 재시도해 주세요.");
            }
        }
        return formView(model, form);
    }

    @GetMapping("/raw-material-lots/{lotId}")
    public String detail(@PathVariable("lotId") Long lotId, Model model) {
        model.addAttribute("lot", service.get(lotId)); return "lot/raw/detail";
    }

    private String formView(Model model, RawMaterialLotForm form) {
        List<Item> choices = List.of();
        try { choices = service.choices(); }
        catch (DataAccessException exception) {
            log.warn("원자재 LOT 품목 조회 실패: {}", exception.getClass().getSimpleName());
            model.addAttribute("choicesError", "원자재 목록을 조회하지 못했습니다. 입력값은 유지됩니다. 연결 상태를 확인한 후 다시 시도해 주세요.");
        }
        model.addAttribute("rawItems", choices);
        model.addAttribute("selectionMissing", form.getItemId() != null && choices.stream().noneMatch(item -> item.getItemId().equals(form.getItemId())));
        return "lot/raw/form";
    }
}
