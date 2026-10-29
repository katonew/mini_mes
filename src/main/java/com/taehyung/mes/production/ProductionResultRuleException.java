package com.taehyung.mes.production;

public class ProductionResultRuleException extends RuntimeException {
    private final String field;
    public ProductionResultRuleException(String field, String message) { super(message); this.field = field; }
    public String getField() { return field; }
}
