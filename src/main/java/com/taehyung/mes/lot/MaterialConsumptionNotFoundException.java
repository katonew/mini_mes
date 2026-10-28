package com.taehyung.mes.lot;

public class MaterialConsumptionNotFoundException extends RuntimeException {
    public MaterialConsumptionNotFoundException() { super("요청한 원자재 소비 이력을 찾을 수 없습니다."); }
}
