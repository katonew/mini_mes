package com.taehyung.mes.lot;

public class RawMaterialLotRuleException extends RuntimeException {
    private final String field;
    public RawMaterialLotRuleException(String field, String message) { super(message); this.field = field; }
    public String getField() { return field; }
}
