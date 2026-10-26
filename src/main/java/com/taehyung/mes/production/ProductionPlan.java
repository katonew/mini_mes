package com.taehyung.mes.production;

/** 생산계획 조회/저장 모델. 계획코드나 계획 상태를 임의로 추가하지 않는다. */
public class ProductionPlan {
    private Long productionPlanId;
    private Long productItemId;
    private java.math.BigDecimal plannedQty;
    private java.time.LocalDate plannedDate;
    private java.time.LocalDateTime createdAt;
    private String createdBy;
    private java.time.LocalDateTime updatedAt;
    private String productCode;
    private String productName;
    private String unitCode;
    private Long workOrderCount;
    public Long getProductionPlanId() { return productionPlanId; }
    public void setProductionPlanId(Long productionPlanId) { this.productionPlanId = productionPlanId; }
    public Long getProductItemId() { return productItemId; }
    public void setProductItemId(Long productItemId) { this.productItemId = productItemId; }
    public java.math.BigDecimal getPlannedQty() { return plannedQty; }
    public void setPlannedQty(java.math.BigDecimal plannedQty) { this.plannedQty = plannedQty; }
    public java.time.LocalDate getPlannedDate() { return plannedDate; }
    public void setPlannedDate(java.time.LocalDate plannedDate) { this.plannedDate = plannedDate; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getUnitCode() { return unitCode; }
    public void setUnitCode(String unitCode) { this.unitCode = unitCode; }
    public Long getWorkOrderCount() { return workOrderCount; }
    public void setWorkOrderCount(Long workOrderCount) { this.workOrderCount = workOrderCount; }
}
