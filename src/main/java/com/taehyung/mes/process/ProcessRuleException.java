package com.taehyung.mes.process;

public class ProcessRuleException extends RuntimeException {
    private final String field;
    public ProcessRuleException(String field, String message) { super(message); this.field = field; }
    public String getField() { return field; }
}
