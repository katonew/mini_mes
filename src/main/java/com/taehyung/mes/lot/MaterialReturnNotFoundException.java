package com.taehyung.mes.lot;

public class MaterialReturnNotFoundException extends RuntimeException {
    public MaterialReturnNotFoundException() { super("요청한 원자재 반환 이력을 찾을 수 없습니다."); }
}
