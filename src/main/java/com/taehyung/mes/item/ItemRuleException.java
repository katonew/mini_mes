package com.taehyung.mes.item;

public class ItemRuleException extends RuntimeException {
    private final String field;

    public ItemRuleException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() { return field; }
}
