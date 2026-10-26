package com.taehyung.mes.bom;

import com.taehyung.mes.item.Item;
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
public class BomController {
    private static final Logger log = LoggerFactory.getLogger(BomController.class);
    private final BomService service;

    public BomController(BomService service) { this.service = service; }

    @InitBinder("bomForm")
    void bindForm(WebDataBinder binder) {
        binder.setAllowedFields("bomCode", "productItemId", "versionNo", "editToken",
                "components[*].bomComponentId", "components[*].materialItemId", "components[*].requiredQty");
        binder.setAutoGrowCollectionLimit(BomService.MAX_COMPONENTS);
    }

    @ModelAttribute("activeMenu")
    String activeMenu() { return "boms"; }

    @GetMapping("/boms")
    public String list(@RequestParam(name = "code", defaultValue = "") String code,
                       @RequestParam(name = "name", defaultValue = "") String name, Model model) {
        model.addAttribute("code", code); model.addAttribute("name", name);
        try { model.addAttribute("boms", service.list(code, name)); }
        catch (BomRuleException exception) {
            model.addAttribute("boms", List.of()); model.addAttribute("searchError", exception.getMessage());
        }
        return "bom/list";
    }

    @GetMapping("/boms/new")
    public String newForm(Model model) {
        BomForm form = new BomForm();
        form.getComponents().add(new BomComponentForm());
        model.addAttribute("bomForm", form);
        return formView(model, form, "create", null, null);
    }

    @GetMapping("/boms/{bomId}/versions/new")
    public String newVersion(@PathVariable("bomId") Long bomId, Model model) {
        Bom bom = service.getHeader(bomId);
        BomForm form = new BomForm(); form.setBomCode(bom.getBomCode()); form.setProductItemId(bom.getProductItemId());
        form.getComponents().add(new BomComponentForm());
        model.addAttribute("bomForm", form);
        return formView(model, form, "version", bomId, null);
    }

    @GetMapping("/boms/versions/{versionId}")
    public String detail(@PathVariable("versionId") Long versionId, Model model) {
        model.addAttribute("version", service.get(versionId));
        return "bom/detail";
    }

    @GetMapping("/boms/versions/{versionId}/edit")
    public String edit(@PathVariable("versionId") Long versionId, Model model) {
        BomVersion version = service.get(versionId);
        if (version.isUsed()) {
            model.addAttribute("version", version);
            model.addAttribute("notice", "사용 이력이 있는 버전은 조회만 가능합니다. 새 버전을 등록해 주세요.");
            return "bom/detail";
        }
        BomForm form = BomForm.from(version);
        model.addAttribute("bomForm", form);
        return formView(model, form, "edit", version.getBomId(), versionId);
    }

    @PostMapping("/boms")
    public String create(@ModelAttribute("bomForm") BomForm form, BindingResult errors, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try { return saved(service.create(form), "BOM을 등록했습니다.", redirect); }
            catch (BomRuleException exception) { rejectRule(errors, exception); }
            catch (DataAccessException exception) { rejectStorage(errors, exception); }
        }
        return formView(model, form, "create", null, null);
    }

    @PostMapping("/boms/{bomId}/versions")
    public String addVersion(@PathVariable("bomId") Long bomId, @ModelAttribute("bomForm") BomForm form,
                             BindingResult errors, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try { return saved(service.addVersion(bomId, form), "BOM 새 버전을 등록했습니다.", redirect); }
            catch (BomRuleException exception) { rejectRule(errors, exception); }
            catch (DataAccessException exception) { rejectStorage(errors, exception); }
        }
        return formView(model, form, "version", bomId, null);
    }

    @PostMapping("/boms/versions/{versionId}")
    public String update(@PathVariable("versionId") Long versionId, @ModelAttribute("bomForm") BomForm form,
                         BindingResult errors, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                service.update(versionId, form);
                return saved(versionId, "BOM 구성 변경사항을 저장했습니다.", redirect);
            } catch (BomRuleException exception) { rejectRule(errors, exception); }
            catch (DataAccessException exception) { rejectStorage(errors, exception); }
        }
        return formView(model, form, "edit", null, versionId);
    }

    private String formView(Model model, BomForm form, String mode, Long bomId, Long versionId) {
        model.addAttribute("mode", mode); model.addAttribute("bomId", bomId); model.addAttribute("versionId", versionId);
        model.addAttribute("maxComponents", BomService.MAX_COMPONENTS);
        List<Long> ids = new ArrayList<>();
        if (form.getProductItemId() != null) { ids.add(form.getProductItemId()); }
        if (form.getComponents() != null) {
            form.getComponents().stream().filter(Objects::nonNull).map(BomComponentForm::getMaterialItemId)
                    .filter(Objects::nonNull).forEach(ids::add);
        }
        Map<Long, Item> selected = new LinkedHashMap<>();
        List<Item> products = new ArrayList<>();
        List<Item> materials = new ArrayList<>();
        try {
            service.displayItems(ids.stream().distinct().toList()).forEach(item -> selected.put(item.getItemId(), item));
            products.addAll(service.products());
            materials.addAll(service.materials());
        } catch (DataAccessException exception) {
            log.warn("BOM 품목 선택 조회 실패: {}", exception.getClass().getSimpleName());
            model.addAttribute("choicesError", "품목 선택 정보를 불러오지 못했습니다. 입력값을 유지한 상태로 연결을 확인해 주세요.");
        }
        // 비활성화되거나 선택 목록에서 빠진 제출값도 오류 화면에서 확인할 수 있게 보존한다.
        selected.values().forEach(item -> {
            if (Objects.equals(item.getItemId(), form.getProductItemId()) && products.stream().noneMatch(p -> p.getItemId().equals(item.getItemId()))) {
                products.add(item);
            }
            if (materials.stream().noneMatch(m -> m.getItemId().equals(item.getItemId()))
                    && form.getComponents() != null && form.getComponents().stream().filter(Objects::nonNull)
                    .anyMatch(row -> Objects.equals(row.getMaterialItemId(), item.getItemId()))) { materials.add(item); }
        });
        model.addAttribute("products", products); model.addAttribute("materials", materials); model.addAttribute("selectedItems", selected);
        return "bom/form";
    }

    private String saved(Long versionId, String message, RedirectAttributes redirect) {
        redirect.addFlashAttribute("successMessage", message);
        return "redirect:/boms/versions/" + versionId;
    }

    private void rejectRule(BindingResult errors, BomRuleException exception) {
        if (exception.getField() == null) { errors.reject("bom.rule", exception.getMessage()); }
        else { errors.rejectValue(exception.getField(), "bom.rule", exception.getMessage()); }
    }

    private void rejectStorage(BindingResult errors, DataAccessException exception) {
        log.warn("BOM 저장 실패: {}", exception.getClass().getSimpleName());
        errors.reject("bom.storage", "데이터베이스 저장에 실패했습니다. 입력값은 유지됩니다. 연결 상태를 확인한 후 다시 시도해 주세요.");
    }
}
