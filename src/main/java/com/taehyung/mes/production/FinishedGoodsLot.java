package com.taehyung.mes.production;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FinishedGoodsLot {
    private Long finishedLotId;
    private Long workOrderId;
    private String lotNo;
    private LocalDateTime createdAt;
    private String createdBy;
    private String productCode;
    private String productName;
    private String workOrderStatus;
    private BigDecimal orderedQty;
    private Long resultCount;
    private BigDecimal producedQty;
    private BigDecimal goodQty;
    private BigDecimal defectQty;
    public Long getFinishedLotId() { return finishedLotId; }
    public void setFinishedLotId(Long finishedLotId) { this.finishedLotId = finishedLotId; }
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public String getLotNo() { return lotNo; }
    public void setLotNo(String lotNo) { this.lotNo = lotNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getWorkOrderStatus() { return workOrderStatus; }
    public void setWorkOrderStatus(String workOrderStatus) { this.workOrderStatus = workOrderStatus; }
    public BigDecimal getOrderedQty() { return orderedQty; }
    public void setOrderedQty(BigDecimal orderedQty) { this.orderedQty = orderedQty; }
    public Long getResultCount() { return resultCount; }
    public void setResultCount(Long resultCount) { this.resultCount = resultCount; }
    public BigDecimal getProducedQty() { return producedQty; }
    public void setProducedQty(BigDecimal producedQty) { this.producedQty = producedQty; }
    public BigDecimal getGoodQty() { return goodQty; }
    public void setGoodQty(BigDecimal goodQty) { this.goodQty = goodQty; }
    public BigDecimal getDefectQty() { return defectQty; }
    public void setDefectQty(BigDecimal defectQty) { this.defectQty = defectQty; }
}
