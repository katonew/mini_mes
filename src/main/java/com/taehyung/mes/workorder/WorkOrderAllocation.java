package com.taehyung.mes.workorder;

public class WorkOrderAllocation {
    private java.math.BigDecimal plannedQty;
    private java.math.BigDecimal allocatedQty;
    private java.math.BigDecimal cancelledQty;
    public java.math.BigDecimal getPlannedQty() { return plannedQty; }
    public void setPlannedQty(java.math.BigDecimal plannedQty) { this.plannedQty = plannedQty; }
    public java.math.BigDecimal getAllocatedQty() { return allocatedQty; }
    public void setAllocatedQty(java.math.BigDecimal allocatedQty) { this.allocatedQty = allocatedQty; }
    public java.math.BigDecimal getCancelledQty() { return cancelledQty; }
    public void setCancelledQty(java.math.BigDecimal cancelledQty) { this.cancelledQty = cancelledQty; }
}
