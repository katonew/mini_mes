package com.taehyung.mes.bom;

public class BomNotFoundException extends RuntimeException {
    public BomNotFoundException() { super("요청한 BOM을 찾을 수 없습니다."); }
}
