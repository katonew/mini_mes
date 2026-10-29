package com.taehyung.mes.quality;

import java.time.LocalDateTime;

public class QualityInspectionReceipt {
    private Long receiptId;
    private String scopeKey;
    private String operationCode;
    private String requestKey;
    private byte[] payloadFingerprint;
    private String snapshot;
    private LocalDateTime processedAt;
    private String createdBy;
    public Long getReceiptId() { return receiptId; }
    public void setReceiptId(Long receiptId) { this.receiptId = receiptId; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public String getOperationCode() { return operationCode; }
    public void setOperationCode(String operationCode) { this.operationCode = operationCode; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public byte[] getPayloadFingerprint() { return payloadFingerprint; }
    public void setPayloadFingerprint(byte[] payloadFingerprint) { this.payloadFingerprint = payloadFingerprint; }
    public String getSnapshot() { return snapshot; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
