package com.taehyung.mes.production;

public class ProductionPlanNotFoundException extends RuntimeException {
    public ProductionPlanNotFoundException() { super("요청한 생산계획을 찾을 수 없습니다."); }
}
