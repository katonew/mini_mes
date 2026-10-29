package com.taehyung.mes.lot;

public class MaterialReturnRuleException extends RuntimeException {
    private final String field;
    public MaterialReturnRuleException(String field, String message) { super(message); this.field = field; }
    public String getField() { return field; }
}
