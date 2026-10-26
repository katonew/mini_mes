package com.taehyung.mes.bom;

public class BomRuleException extends RuntimeException {
    private final String field;

    public BomRuleException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() { return field; }
}
