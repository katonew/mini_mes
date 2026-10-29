package com.taehyung.mes.quality;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class QualityInspection {
    private Long inspectionId;
    private Long finishedLotId;
    private Long receiptId;
    private Long roundNo;
    private BigDecimal inspectedQty;
    private BigDecimal acceptedQty;
    private BigDecimal rejectedQty;
    private LocalDateTime inspectedAt;
    private String inspectorRef;
    private String judgementReason;
    private LocalDateTime createdAt;
    private String createdBy;
    private Long workOrderId;
    private String lotNo;
    private String productCode;
    private String productName;
    private String snapshot;
    public Long getInspectionId() { return inspectionId; }
    public void setInspectionId(Long inspectionId) { this.inspectionId = inspectionId; }
    public Long getFinishedLotId() { return finishedLotId; }
    public void setFinishedLotId(Long finishedLotId) { this.finishedLotId = finishedLotId; }
    public Long getReceiptId() { return receiptId; }
    public void setReceiptId(Long receiptId) { this.receiptId = receiptId; }
    public Long getRoundNo() { return roundNo; }
    public void setRoundNo(Long roundNo) { this.roundNo = roundNo; }
    public BigDecimal getInspectedQty() { return inspectedQty; }
    public void setInspectedQty(BigDecimal inspectedQty) { this.inspectedQty = inspectedQty; }
    public BigDecimal getAcceptedQty() { return acceptedQty; }
    public void setAcceptedQty(BigDecimal acceptedQty) { this.acceptedQty = acceptedQty; }
    public BigDecimal getRejectedQty() { return rejectedQty; }
    public void setRejectedQty(BigDecimal rejectedQty) { this.rejectedQty = rejectedQty; }
    public LocalDateTime getInspectedAt() { return inspectedAt; }
    public void setInspectedAt(LocalDateTime inspectedAt) { this.inspectedAt = inspectedAt; }
    public String getInspectorRef() { return inspectorRef; }
    public void setInspectorRef(String inspectorRef) { this.inspectorRef = inspectorRef; }
    public String getJudgementReason() { return judgementReason; }
    public void setJudgementReason(String judgementReason) { this.judgementReason = judgementReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public String getLotNo() { return lotNo; }
    public void setLotNo(String lotNo) { this.lotNo = lotNo; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }
}