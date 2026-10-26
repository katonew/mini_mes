package com.taehyung.mes.common.web;

import com.taehyung.mes.item.ItemNotFoundException;
import com.taehyung.mes.bom.BomNotFoundException;
import com.taehyung.mes.process.ProcessNotFoundException;
import com.taehyung.mes.routing.RoutingNotFoundException;
import com.taehyung.mes.production.ProductionPlanNotFoundException;
import com.taehyung.mes.workorder.WorkOrderNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.InvalidPropertyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class PageExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(PageExceptionHandler.class);

    @ExceptionHandler(WorkOrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String workOrderNotFound(Model model) {
        model.addAttribute("safeTitle", "작업지시를 찾을 수 없습니다");
        model.addAttribute("safeMessage", "작업지시 목록에서 대상을 다시 선택해 주세요.");
        model.addAttribute("activeMenu", "work-orders");
        return "error";
    }

    @ExceptionHandler(ProductionPlanNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String productionPlanNotFound(Model model) {
        model.addAttribute("safeTitle", "생산계획을 찾을 수 없습니다");
        model.addAttribute("safeMessage", "생산계획 목록에서 대상을 다시 선택해 주세요.");
        model.addAttribute("activeMenu", "production-plans");
        return "error";
    }

    @ExceptionHandler(RoutingNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String routingNotFound(Model model) {
        model.addAttribute("safeTitle", "Routing을 찾을 수 없습니다");
        model.addAttribute("safeMessage", "Routing 목록에서 대상 개정을 다시 선택해 주세요.");
        model.addAttribute("activeMenu", "routings");
        return "error";
    }

    @ExceptionHandler(ProcessNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String processNotFound(Model model) {
        model.addAttribute("safeTitle", "공정을 찾을 수 없습니다");
        model.addAttribute("safeMessage", "공정 목록에서 대상 공정을 다시 선택해 주세요.");
        model.addAttribute("activeMenu", "processes");
        return "error";
    }

    @ExceptionHandler(BomNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String bomNotFound(Model model) {
        model.addAttribute("safeTitle", "BOM을 찾을 수 없습니다");
        model.addAttribute("safeMessage", "BOM 목록에서 대상 버전을 다시 선택해 주세요.");
        model.addAttribute("returnToBoms", true);
        model.addAttribute("activeMenu", "boms");
        return "error";
    }

    @ExceptionHandler(InvalidPropertyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalidForm(Model model, HttpServletRequest request) {
        model.addAttribute("safeTitle", "입력 형식을 확인해 주세요");
        model.addAttribute("safeMessage", "구성행 입력 형식이나 허용 개수를 벗어났습니다. 목록에서 다시 등록해 주세요.");
        model.addAttribute("returnToBoms", true);
        model.addAttribute("activeMenu", "boms");
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/routings") || path.startsWith("/routings/")) { model.addAttribute("activeMenu", "routings"); }
        if (path.equals("/production-plans") || path.startsWith("/production-plans/")) { model.addAttribute("activeMenu", "production-plans"); }
        if (path.equals("/work-orders") || path.startsWith("/work-orders/")) { model.addAttribute("activeMenu", "work-orders"); }
        return "error";
    }

    @ExceptionHandler(ItemNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String itemNotFound(Model model) {
        model.addAttribute("safeTitle", "품목을 찾을 수 없습니다");
        model.addAttribute("safeMessage", "품목 목록에서 대상 품목을 다시 선택해 주세요.");
        return "error";
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String databaseUnavailable(DataAccessException exception, Model model, HttpServletRequest request) {
        log.warn("데이터 조회 실패: {}", exception.getClass().getSimpleName());
        model.addAttribute("safeTitle", "데이터를 불러오지 못했습니다");
        model.addAttribute("safeMessage", "데이터베이스 연결 상태를 확인한 후 다시 시도해 주세요.");
        // 예외 처리에는 새 모델이 사용되므로 Controller의 메뉴값을 다시 지정한다.
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/processes") || path.startsWith("/processes/")) {
            model.addAttribute("activeMenu", "processes");
        }
        if (path.equals("/routings") || path.startsWith("/routings/")) { model.addAttribute("activeMenu", "routings"); }
        if (path.equals("/production-plans") || path.startsWith("/production-plans/")) { model.addAttribute("activeMenu", "production-plans"); }
        if (path.equals("/work-orders") || path.startsWith("/work-orders/")) { model.addAttribute("activeMenu", "work-orders"); }
        return "error";
    }
}
