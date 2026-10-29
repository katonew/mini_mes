package com.taehyung.mes.quality;

public class QualityInspectionRuleException extends RuntimeException {
    private final String field;
    public QualityInspectionRuleException(String field, String message) { super(message); this.field = field; }
    public String getField() { return field; }
}
