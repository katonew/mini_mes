package com.taehyung.mes.production;

public class ProductionPlanRuleException extends RuntimeException {
    private final String field;
    public ProductionPlanRuleException(String field, String message) { super(message); this.field = field; }
    public String getField() { return field; }
}
