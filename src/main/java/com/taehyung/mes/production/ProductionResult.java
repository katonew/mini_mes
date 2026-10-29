package com.taehyung.mes.production;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductionResult {
    private Long productionResultId;
    private Long finishedLotId;
    private Long workOrderId;
    private Long receiptId;
    private String lotNo;
    private String productCode;
    private String productName;
    private BigDecimal producedQty;
    private BigDecimal goodQty;
    private BigDecimal defectQty;
    private LocalDateTime producedAt;
    private String workerRef;
    private Long revisionNo;
    private LocalDateTime createdAt;
    private String createdBy;
    private String snapshot;
    private ProductionResultOutcome outcome;
    public Long getProductionResultId() { return productionResultId; }
    public void setProductionResultId(Long productionResultId) { this.productionResultId = productionResultId; }
    public Long getFinishedLotId() { return finishedLotId; }
    public void setFinishedLotId(Long finishedLotId) { this.finishedLotId = finishedLotId; }
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public Long getReceiptId() { return receiptId; }
    public void setReceiptId(Long receiptId) { this.receiptId = receiptId; }
    public String getLotNo() { return lotNo; }
    public void setLotNo(String lotNo) { this.lotNo = lotNo; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public BigDecimal getProducedQty() { return producedQty; }
    public void setProducedQty(BigDecimal producedQty) { this.producedQty = producedQty; }
    public BigDecimal getGoodQty() { return goodQty; }
    public void setGoodQty(BigDecimal goodQty) { this.goodQty = goodQty; }
    public BigDecimal getDefectQty() { return defectQty; }
    public void setDefectQty(BigDecimal defectQty) { this.defectQty = defectQty; }
    public LocalDateTime getProducedAt() { return producedAt; }
    public void setProducedAt(LocalDateTime producedAt) { this.producedAt = producedAt; }
    public String getWorkerRef() { return workerRef; }
    public void setWorkerRef(String workerRef) { this.workerRef = workerRef; }
    public Long getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Long revisionNo) { this.revisionNo = revisionNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }
    public ProductionResultOutcome getOutcome() { return outcome; }
    public void setOutcome(ProductionResultOutcome outcome) { this.outcome = outcome; }
}
