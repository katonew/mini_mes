package com.taehyung.mes.workorder;

public class WorkOrderDetails {
    private WorkOrder order;
    private WorkOrderTotals totals;
    private java.util.List<com.taehyung.mes.routing.RoutingStep> steps;
    private boolean completionReady;
    private com.taehyung.mes.production.FinishedGoodsLot finishedLot;
    private java.util.List<com.taehyung.mes.production.ProductionResult> results;
    public WorkOrder getOrder() { return order; }
    public void setOrder(WorkOrder order) { this.order = order; }
    public WorkOrderTotals getTotals() { return totals; }
    public void setTotals(WorkOrderTotals totals) { this.totals = totals; }
    public java.util.List<com.taehyung.mes.routing.RoutingStep> getSteps() { return steps; }
    public void setSteps(java.util.List<com.taehyung.mes.routing.RoutingStep> steps) { this.steps = steps; }
    public boolean getCompletionReady() { return completionReady; }
    public void setCompletionReady(boolean completionReady) { this.completionReady = completionReady; }
    public com.taehyung.mes.production.FinishedGoodsLot getFinishedLot() { return finishedLot; }
    public void setFinishedLot(com.taehyung.mes.production.FinishedGoodsLot finishedLot) { this.finishedLot = finishedLot; }
    public java.util.List<com.taehyung.mes.production.ProductionResult> getResults() { return results; }
    public void setResults(java.util.List<com.taehyung.mes.production.ProductionResult> results) { this.results = results; }
}
