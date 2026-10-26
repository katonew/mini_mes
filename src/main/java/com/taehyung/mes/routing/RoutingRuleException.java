package com.taehyung.mes.routing;

public class RoutingRuleException extends RuntimeException {
    private final String field;
    public RoutingRuleException(String field, String message) { super(message); this.field = field; }
    public String getField() { return field; }
}
