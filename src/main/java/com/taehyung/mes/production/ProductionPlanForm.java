package com.taehyung.mes.production;

/** 등록 요청 전용. PK·작성자·상태·BOM·Routing은 바인딩하지 않는다. */
public class ProductionPlanForm {
    private Long productItemId;
    private java.math.BigDecimal plannedQty;
    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private java.time.LocalDate plannedDate;
    public Long getProductItemId() { return productItemId; }
    public void setProductItemId(Long productItemId) { this.productItemId = productItemId; }
    public java.math.BigDecimal getPlannedQty() { return plannedQty; }
    public void setPlannedQty(java.math.BigDecimal plannedQty) { this.plannedQty = plannedQty; }
    public java.time.LocalDate getPlannedDate() { return plannedDate; }
    public void setPlannedDate(java.time.LocalDate plannedDate) { this.plannedDate = plannedDate; }
}
