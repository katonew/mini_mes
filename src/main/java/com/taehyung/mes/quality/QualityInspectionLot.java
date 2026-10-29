package com.taehyung.mes.quality;

import java.math.BigDecimal;

public class QualityInspectionLot {
    private Long finishedLotId;
    private Long workOrderId;
    private String lotNo;
    private String productCode;
    private String productName;
    private String workOrderStatus;
    private String productUnitCode;
    private BigDecimal inspectionTargetQty;
    private BigDecimal inspectedQty;
    private BigDecimal acceptedQty;
    private BigDecimal rejectedQty;
    private Long lastRoundNo;
    public Long getFinishedLotId() { return finishedLotId; }
    public void setFinishedLotId(Long finishedLotId) { this.finishedLotId = finishedLotId; }
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public String getLotNo() { return lotNo; }
    public void setLotNo(String lotNo) { this.lotNo = lotNo; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getWorkOrderStatus() { return workOrderStatus; }
    public void setWorkOrderStatus(String workOrderStatus) { this.workOrderStatus = workOrderStatus; }
    public String getProductUnitCode() { return productUnitCode; }
    public void setProductUnitCode(String productUnitCode) { this.productUnitCode = productUnitCode; }
    public BigDecimal getInspectionTargetQty() { return inspectionTargetQty; }
    public void setInspectionTargetQty(BigDecimal inspectionTargetQty) { this.inspectionTargetQty = inspectionTargetQty; }
    public BigDecimal getInspectedQty() { return inspectedQty; }
    public void setInspectedQty(BigDecimal inspectedQty) { this.inspectedQty = inspectedQty; }
    public BigDecimal getAcceptedQty() { return acceptedQty; }
    public void setAcceptedQty(BigDecimal acceptedQty) { this.acceptedQty = acceptedQty; }
    public BigDecimal getRejectedQty() { return rejectedQty; }
    public void setRejectedQty(BigDecimal rejectedQty) { this.rejectedQty = rejectedQty; }
    public Long getLastRoundNo() { return lastRoundNo; }
    public void setLastRoundNo(Long lastRoundNo) { this.lastRoundNo = lastRoundNo; }
    public BigDecimal getUninspectedQty() {
        return inspectionTargetQty == null ? null : inspectionTargetQty.subtract(acceptedQty).subtract(rejectedQty);
    }
    public boolean isRegistrationAllowed() {
        return "COMPLETED".equals(workOrderStatus) && "EA".equals(productUnitCode)
                && getUninspectedQty() != null && getUninspectedQty().signum() > 0;
    }
}
