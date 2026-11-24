package com.taehyung.mes.trace;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import com.taehyung.mes.lot.RawMaterialLotNotFoundException;
import com.taehyung.mes.production.ProductionResultNotFoundException;
import java.util.List;

@Controller
public class LotTraceController {
    private final LotTraceService service;
    public LotTraceController(LotTraceService service) { this.service = service; }
    @ModelAttribute("activeMenu") String activeMenu() { return "lot-traces"; }
    @GetMapping("/lot-traces")
    public String list(@RequestParam(name="direction", defaultValue="forward") String direction,
                       @RequestParam(name="lotNo", defaultValue="") String lotNo, Model model) {
        model.addAttribute("direction", direction); model.addAttribute("lotNo", lotNo);
        model.addAttribute("lots", List.of());
        if (!"forward".equals(direction) && !"backward".equals(direction)) {
            model.addAttribute("searchError", "정방향 또는 역방향 추적을 선택해 주세요.");
        } else if (lotNo.length() > 64) {
            model.addAttribute("searchError", "LOT 번호는 64자 이내로 입력해 주세요.");
        } else {
            model.addAttribute("lots", "forward".equals(direction) ? service.searchRaw(lotNo) : service.searchFinished(lotNo));
        }
        return "trace/list";
    }
    @GetMapping("/raw-material-lots/{lotId}/trace")
    public String forward(@PathVariable("lotId") Long lotId, Model model) {
        model.addAttribute("trace", service.forward(lotId)); return "trace/forward";
    }
    @GetMapping("/finished-goods-lots/{lotId}/trace")
    public String detail(@PathVariable("lotId") Long lotId, Model model) {
        model.addAttribute("trace", service.get(lotId)); return "trace/detail";
    }
    @ExceptionHandler({RawMaterialLotNotFoundException.class, ProductionResultNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(RuntimeException exception, Model model) {
        model.addAttribute("activeMenu", "lot-traces");
        model.addAttribute("direction", exception instanceof ProductionResultNotFoundException ? "backward" : "forward");
        model.addAttribute("lotNo", ""); model.addAttribute("lots", List.of());
        model.addAttribute("searchError", "요청한 LOT을 찾을 수 없습니다. LOT 번호로 다시 검색해 주세요.");
        return "trace/list";
    }
}
