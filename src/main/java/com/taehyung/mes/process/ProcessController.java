package com.taehyung.mes.process;

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
public class ProcessController {
    private static final Logger log = LoggerFactory.getLogger(ProcessController.class);
    private final ProcessService service;

    public ProcessController(ProcessService service) { this.service = service; }

    @InitBinder("processForm")
    void bindForm(WebDataBinder binder) {
        binder.setAllowedFields("processCode", "processName", "description", "isActive");
    }

    @ModelAttribute("activeMenu")
    String activeMenu() { return "processes"; }

    @GetMapping("/processes")
    public String list(@RequestParam(name = "code", defaultValue = "") String code,
                       @RequestParam(name = "name", defaultValue = "") String name, Model model) {
        model.addAttribute("code", code);
        model.addAttribute("name", name);
        try {
            model.addAttribute("processes", service.list(code, name));
        } catch (ProcessRuleException exception) {
            model.addAttribute("processes", List.of());
            model.addAttribute("searchError", exception.getMessage());
        }
        return "process/list";
    }

    @GetMapping("/processes/new")
    public String newForm(Model model) {
        ProcessForm form = new ProcessForm();
        form.setIsActive(1);
        model.addAttribute("processForm", form);
        return formView(model, false, null);
    }

    @PostMapping("/processes")
    public String create(@ModelAttribute("processForm") ProcessForm form, BindingResult errors,
                         Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                service.create(form);
                redirect.addFlashAttribute("successMessage", "공정을 등록했습니다.");
                return "redirect:/processes";
            } catch (ProcessRuleException exception) {
                rejectRule(errors, exception);
            } catch (DataAccessException exception) {
                rejectStorage(errors, exception);
            }
        }
        return formView(model, false, null);
    }

    @GetMapping("/processes/{processId}/edit")
    public String editForm(@PathVariable("processId") Long processId, Model model) {
        model.addAttribute("processForm", ProcessForm.from(service.get(processId)));
        return formView(model, true, processId);
    }

    @PostMapping("/processes/{processId}")
    public String update(@PathVariable("processId") Long processId,
                         @ModelAttribute("processForm") ProcessForm form, BindingResult errors,
                         Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                service.update(processId, form);
                redirect.addFlashAttribute("successMessage", "공정을 수정했습니다.");
                return "redirect:/processes";
            } catch (ProcessRuleException exception) {
                rejectRule(errors, exception);
            } catch (DataAccessException exception) {
                rejectStorage(errors, exception);
            }
        }
        return formView(model, true, processId);
    }

    private String formView(Model model, boolean editing, Long processId) {
        model.addAttribute("editing", editing);
        model.addAttribute("processId", processId);
        return "process/form";
    }

    private void rejectRule(BindingResult errors, ProcessRuleException exception) {
        if (exception.getField() == null) {
            errors.reject("process.rule", exception.getMessage());
        } else {
            errors.rejectValue(exception.getField(), "process.rule", exception.getMessage());
        }
    }

    private void rejectStorage(BindingResult errors, DataAccessException exception) {
        log.warn("공정 저장 실패: {}", exception.getClass().getSimpleName());
        errors.reject("process.storage", "데이터베이스 저장에 실패했습니다. 입력값은 유지됩니다. 연결 상태를 확인한 후 다시 시도해 주세요.");
    }
}
