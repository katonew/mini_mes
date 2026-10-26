package com.taehyung.mes.workorder;

public class WorkOrder {
    private Long workOrderId;
    private Long productionPlanId;
    private Long productItemId;
    private Long bomVersionId;
    private Long routingRevisionId;
    private java.math.BigDecimal orderedQty;
    private String productUnitCode;
    private String status;
    private java.math.BigDecimal inspectionTargetQty;
    private java.time.LocalDateTime createdAt;
    private String createdBy;
    private java.time.LocalDateTime startedAt;
    private String startedBy;
    private java.time.LocalDateTime cancelledAt;
    private String cancelledBy;
    private java.time.LocalDateTime completedAt;
    private String completedBy;
    private String productCode;
    private String productName;
    private String bomCode;
    private Long bomVersionNo;
    private String routingCode;
    private Long routingRevisionNo;
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public Long getProductionPlanId() { return productionPlanId; }
    public void setProductionPlanId(Long productionPlanId) { this.productionPlanId = productionPlanId; }
    public Long getProductItemId() { return productItemId; }
    public void setProductItemId(Long productItemId) { this.productItemId = productItemId; }
    public Long getBomVersionId() { return bomVersionId; }
    public void setBomVersionId(Long bomVersionId) { this.bomVersionId = bomVersionId; }
    public Long getRoutingRevisionId() { return routingRevisionId; }
    public void setRoutingRevisionId(Long routingRevisionId) { this.routingRevisionId = routingRevisionId; }
    public java.math.BigDecimal getOrderedQty() { return orderedQty; }
    public void setOrderedQty(java.math.BigDecimal orderedQty) { this.orderedQty = orderedQty; }
    public String getProductUnitCode() { return productUnitCode; }
    public void setProductUnitCode(String productUnitCode) { this.productUnitCode = productUnitCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public java.math.BigDecimal getInspectionTargetQty() { return inspectionTargetQty; }
    public void setInspectionTargetQty(java.math.BigDecimal inspectionTargetQty) { this.inspectionTargetQty = inspectionTargetQty; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public java.time.LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(java.time.LocalDateTime startedAt) { this.startedAt = startedAt; }
    public String getStartedBy() { return startedBy; }
    public void setStartedBy(String startedBy) { this.startedBy = startedBy; }
    public java.time.LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(java.time.LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }
    public java.time.LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(java.time.LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getCompletedBy() { return completedBy; }
    public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getBomCode() { return bomCode; }
    public void setBomCode(String bomCode) { this.bomCode = bomCode; }
    public Long getBomVersionNo() { return bomVersionNo; }
    public void setBomVersionNo(Long bomVersionNo) { this.bomVersionNo = bomVersionNo; }
    public String getRoutingCode() { return routingCode; }
    public void setRoutingCode(String routingCode) { this.routingCode = routingCode; }
    public Long getRoutingRevisionNo() { return routingRevisionNo; }
    public void setRoutingRevisionNo(Long routingRevisionNo) { this.routingRevisionNo = routingRevisionNo; }
    public String getStatusLabel() { return WorkOrderStatus.labelOf(status); }
}
