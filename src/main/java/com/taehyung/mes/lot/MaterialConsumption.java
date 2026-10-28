package com.taehyung.mes.lot;

public class MaterialConsumption {
    private Long consumptionId;
    private Long workOrderId;
    private Long rawLotId;
    private Long bomComponentId;
    private Long receiptId;
    private java.math.BigDecimal consumedQty;
    private java.math.BigDecimal returnedQty;
    private java.math.BigDecimal availableQty;
    private java.time.LocalDateTime consumedAt;
    private java.time.LocalDateTime createdAt;
    private String createdBy;
    private String lotNo;
    private String itemCode;
    private String itemName;
    private String unitCode;
    private String workOrderStatus;
    private String bomCode;
    private Long bomVersionNo;
    private String snapshot;
    private MaterialConsumptionOutcome outcome;
    public Long getConsumptionId() { return consumptionId; }
    public void setConsumptionId(Long consumptionId) { this.consumptionId = consumptionId; }
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public Long getRawLotId() { return rawLotId; }
    public void setRawLotId(Long rawLotId) { this.rawLotId = rawLotId; }
    public Long getBomComponentId() { return bomComponentId; }
    public void setBomComponentId(Long bomComponentId) { this.bomComponentId = bomComponentId; }
    public Long getReceiptId() { return receiptId; }
    public void setReceiptId(Long receiptId) { this.receiptId = receiptId; }
    public java.math.BigDecimal getConsumedQty() { return consumedQty; }
    public void setConsumedQty(java.math.BigDecimal consumedQty) { this.consumedQty = consumedQty; }
    public java.math.BigDecimal getReturnedQty() { return returnedQty; }
    public void setReturnedQty(java.math.BigDecimal returnedQty) { this.returnedQty = returnedQty; }
    public java.math.BigDecimal getAvailableQty() { return availableQty; }
    public void setAvailableQty(java.math.BigDecimal availableQty) { this.availableQty = availableQty; }
    public java.time.LocalDateTime getConsumedAt() { return consumedAt; }
    public void setConsumedAt(java.time.LocalDateTime consumedAt) { this.consumedAt = consumedAt; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getLotNo() { return lotNo; }
    public void setLotNo(String lotNo) { this.lotNo = lotNo; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getUnitCode() { return unitCode; }
    public void setUnitCode(String unitCode) { this.unitCode = unitCode; }
    public String getWorkOrderStatus() { return workOrderStatus; }
    public void setWorkOrderStatus(String workOrderStatus) { this.workOrderStatus = workOrderStatus; }
    public String getBomCode() { return bomCode; }
    public void setBomCode(String bomCode) { this.bomCode = bomCode; }
    public Long getBomVersionNo() { return bomVersionNo; }
    public void setBomVersionNo(Long bomVersionNo) { this.bomVersionNo = bomVersionNo; }
    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }
    public MaterialConsumptionOutcome getOutcome() { return outcome; }
    public void setOutcome(MaterialConsumptionOutcome outcome) { this.outcome = outcome; }
}
