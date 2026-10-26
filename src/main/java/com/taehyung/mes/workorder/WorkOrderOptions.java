package com.taehyung.mes.workorder;

public class WorkOrderOptions {
    private boolean productActive;
    public boolean getProductActive() { return productActive; }
    public void setProductActive(boolean productActive) { this.productActive = productActive; }
    private com.taehyung.mes.production.ProductionPlan plan;
    private WorkOrderAllocation allocation;
    private java.util.List<WorkOrderBomOption> boms;
    private java.util.List<WorkOrderRoutingOption> routings;
    public com.taehyung.mes.production.ProductionPlan getPlan() { return plan; }
    public void setPlan(com.taehyung.mes.production.ProductionPlan plan) { this.plan = plan; }
    public WorkOrderAllocation getAllocation() { return allocation; }
    public void setAllocation(WorkOrderAllocation allocation) { this.allocation = allocation; }
    public java.util.List<WorkOrderBomOption> getBoms() { return boms; }
    public void setBoms(java.util.List<WorkOrderBomOption> boms) { this.boms = boms; }
    public java.util.List<WorkOrderRoutingOption> getRoutings() { return routings; }
    public void setRoutings(java.util.List<WorkOrderRoutingOption> routings) { this.routings = routings; }
}
