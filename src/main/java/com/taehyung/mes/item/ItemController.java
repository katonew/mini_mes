package com.taehyung.mes.item;

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
public class ItemController {
    private static final Logger log = LoggerFactory.getLogger(ItemController.class);
    private final ItemService service;

    public ItemController(ItemService service) { this.service = service; }

    @InitBinder("itemForm")
    void bindForm(WebDataBinder binder) {
        binder.setAllowedFields("itemCode", "itemName", "itemKind", "unitCode", "isActive");
    }

    @ModelAttribute("itemKinds")
    List<ItemKind> itemKinds() { return ItemKind.registrationOptions(); }

    @ModelAttribute("units")
    ItemUnit[] units() { return ItemUnit.values(); }

    @GetMapping("/")
    public String home() { return "redirect:/dashboard"; }

    @GetMapping("/items")
    public String list(@RequestParam(name = "code", defaultValue = "") String code,
                       @RequestParam(name = "name", defaultValue = "") String name, Model model) {
        model.addAttribute("code", code);
        model.addAttribute("name", name);
        try {
            model.addAttribute("items", service.list(code, name));
        } catch (ItemRuleException exception) {
            model.addAttribute("items", List.of());
            model.addAttribute("searchError", exception.getMessage());
        }
        return "item/list";
    }

    @GetMapping("/items/new")
    public String newForm(Model model) {
        ItemForm form = new ItemForm();
        form.setIsActive(1);
        model.addAttribute("itemForm", form);
        return formView(model, false, null);
    }

    @PostMapping("/items")
    public String create(@ModelAttribute("itemForm") ItemForm form, BindingResult errors,
                         Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                service.create(form);
                redirect.addFlashAttribute("successMessage", "품목을 등록했습니다.");
                return "redirect:/items";
            } catch (ItemRuleException exception) {
                rejectRule(errors, exception);
            } catch (DataAccessException exception) {
                rejectStorage(errors, exception);
            }
        }
        return formView(model, false, null);
    }

    @GetMapping("/items/{itemId}/edit")
    public String editForm(@PathVariable("itemId") Long itemId, Model model) {
        model.addAttribute("itemForm", ItemForm.from(service.get(itemId)));
        return formView(model, true, itemId);
    }

    @PostMapping("/items/{itemId}")
    public String update(@PathVariable("itemId") Long itemId,
                         @ModelAttribute("itemForm") ItemForm form, BindingResult errors,
                         Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                service.update(itemId, form);
                redirect.addFlashAttribute("successMessage", "품목을 수정했습니다.");
                return "redirect:/items";
            } catch (ItemRuleException exception) {
                rejectRule(errors, exception);
            } catch (DataAccessException exception) {
                rejectStorage(errors, exception);
            }
        }
        return formView(model, true, itemId);
    }

    private String formView(Model model, boolean editing, Long itemId) {
        model.addAttribute("editing", editing);
        model.addAttribute("itemId", itemId);
        return "item/form";
    }

    private void rejectRule(BindingResult errors, ItemRuleException exception) {
        if (exception.getField() == null) {
            errors.reject("item.rule", exception.getMessage());
        } else {
            errors.rejectValue(exception.getField(), "item.rule", exception.getMessage());
        }
    }

    private void rejectStorage(BindingResult errors, DataAccessException exception) {
        log.warn("품목 저장 실패: {}", exception.getClass().getSimpleName());
        errors.reject("item.storage", "데이터베이스 저장에 실패했습니다. 입력값은 유지됩니다. 연결 상태를 확인한 후 다시 시도해 주세요.");
    }
}
