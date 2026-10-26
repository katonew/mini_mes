package com.taehyung.mes.common.web;

import com.taehyung.mes.item.ItemNotFoundException;
import com.taehyung.mes.bom.BomNotFoundException;
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
    public String invalidForm(Model model) {
        model.addAttribute("safeTitle", "입력 형식을 확인해 주세요");
        model.addAttribute("safeMessage", "구성행 입력 형식이나 허용 개수를 벗어났습니다. 목록에서 다시 등록해 주세요.");
        model.addAttribute("returnToBoms", true);
        model.addAttribute("activeMenu", "boms");
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
    public String databaseUnavailable(DataAccessException exception, Model model) {
        log.warn("데이터 조회 실패: {}", exception.getClass().getSimpleName());
        model.addAttribute("safeTitle", "데이터를 불러오지 못했습니다");
        model.addAttribute("safeMessage", "데이터베이스 연결 상태를 확인한 후 다시 시도해 주세요.");
        return "error";
    }
}
