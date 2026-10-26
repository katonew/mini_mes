package com.taehyung.mes.workorder;

public class WorkOrderDetails {
    private WorkOrder order;
    private WorkOrderTotals totals;
    private java.util.List<com.taehyung.mes.routing.RoutingStep> steps;
    private boolean completionReady;
    public WorkOrder getOrder() { return order; }
    public void setOrder(WorkOrder order) { this.order = order; }
    public WorkOrderTotals getTotals() { return totals; }
    public void setTotals(WorkOrderTotals totals) { this.totals = totals; }
    public java.util.List<com.taehyung.mes.routing.RoutingStep> getSteps() { return steps; }
    public void setSteps(java.util.List<com.taehyung.mes.routing.RoutingStep> steps) { this.steps = steps; }
    public boolean getCompletionReady() { return completionReady; }
    public void setCompletionReady(boolean completionReady) { this.completionReady = completionReady; }
}
