package com.taehyung.mes.lot;

public class MaterialConsumptionRuleException extends RuntimeException {
    private final String field;
    public MaterialConsumptionRuleException(String field, String message) { super(message); this.field = field; }
    public String getField() { return field; }
}
