package com.taehyung.mes.routing;

public class RoutingRevision {
    private Long routingRevisionId;
    private Long routingId;
    private Long revisionNo;
    private String routingCode;
    private Long productItemId;
    private String productCode;
    private String productName;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime firstUsedAt;
    private boolean workOrderReferenced;
    private String createdBy;
    private java.util.List<RoutingStep> steps = new java.util.ArrayList<>();
    public Long getRoutingRevisionId() { return routingRevisionId; }
    public void setRoutingRevisionId(Long routingRevisionId) { this.routingRevisionId = routingRevisionId; }
    public Long getRoutingId() { return routingId; }
    public void setRoutingId(Long routingId) { this.routingId = routingId; }
    public Long getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Long revisionNo) { this.revisionNo = revisionNo; }
    public String getRoutingCode() { return routingCode; }
    public void setRoutingCode(String routingCode) { this.routingCode = routingCode; }
    public Long getProductItemId() { return productItemId; }
    public void setProductItemId(Long productItemId) { this.productItemId = productItemId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    public java.time.LocalDateTime getFirstUsedAt() { return firstUsedAt; }
    public void setFirstUsedAt(java.time.LocalDateTime firstUsedAt) { this.firstUsedAt = firstUsedAt; }
    public boolean getWorkOrderReferenced() { return workOrderReferenced; }
    public void setWorkOrderReferenced(boolean workOrderReferenced) { this.workOrderReferenced = workOrderReferenced; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public java.util.List<RoutingStep> getSteps() { return steps; }
    public void setSteps(java.util.List<RoutingStep> steps) { this.steps = steps; }
    public boolean isUsed() { return firstUsedAt != null || workOrderReferenced; }
}
