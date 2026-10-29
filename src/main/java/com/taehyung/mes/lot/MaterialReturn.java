package com.taehyung.mes.lot;

public class MaterialReturn {
    private Long returnId;
    private Long consumptionId;
    private Long receiptId;
    private Long workOrderId;
    private Long rawLotId;
    private java.math.BigDecimal returnedQty;
    private java.math.BigDecimal consumedQty;
    private java.math.BigDecimal totalReturnedQty;
    private java.math.BigDecimal availableQty;
    private java.time.LocalDateTime returnedAt;
    private String reason;
    private String createdBy;
    private String lotNo;
    private String itemCode;
    private String itemName;
    private String unitCode;
    private String workOrderStatus;
    private String snapshot;
    private MaterialReturnOutcome outcome;
    public Long getReturnId() { return returnId; }
    public void setReturnId(Long returnId) { this.returnId = returnId; }
    public Long getConsumptionId() { return consumptionId; }
    public void setConsumptionId(Long consumptionId) { this.consumptionId = consumptionId; }
    public Long getReceiptId() { return receiptId; }
    public void setReceiptId(Long receiptId) { this.receiptId = receiptId; }
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public Long getRawLotId() { return rawLotId; }
    public void setRawLotId(Long rawLotId) { this.rawLotId = rawLotId; }
    public java.math.BigDecimal getReturnedQty() { return returnedQty; }
    public void setReturnedQty(java.math.BigDecimal returnedQty) { this.returnedQty = returnedQty; }
    public java.math.BigDecimal getConsumedQty() { return consumedQty; }
    public void setConsumedQty(java.math.BigDecimal consumedQty) { this.consumedQty = consumedQty; }
    public java.math.BigDecimal getTotalReturnedQty() { return totalReturnedQty; }
    public void setTotalReturnedQty(java.math.BigDecimal totalReturnedQty) { this.totalReturnedQty = totalReturnedQty; }
    public java.math.BigDecimal getAvailableQty() { return availableQty; }
    public void setAvailableQty(java.math.BigDecimal availableQty) { this.availableQty = availableQty; }
    public java.time.LocalDateTime getReturnedAt() { return returnedAt; }
    public void setReturnedAt(java.time.LocalDateTime returnedAt) { this.returnedAt = returnedAt; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
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
    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }
    public MaterialReturnOutcome getOutcome() { return outcome; }
    public void setOutcome(MaterialReturnOutcome outcome) { this.outcome = outcome; }
    public java.math.BigDecimal getReturnableQty() { return consumedQty.subtract(totalReturnedQty); }
}
