package com.taehyung.mes.workorder;

public class WorkOrderRuleException extends RuntimeException {
    private final String field;
    public WorkOrderRuleException(String field, String message) { super(message); this.field = field; }
    public String getField() { return field; }
}
