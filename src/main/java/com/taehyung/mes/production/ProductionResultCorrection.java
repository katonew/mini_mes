package com.taehyung.mes.production;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductionResultCorrection {
    private Long correctionId;
    private Long productionResultId;
    private Long fromRevision;
    private Long toRevision;
    private BigDecimal beforeProducedQty;
    private BigDecimal afterProducedQty;
    private BigDecimal beforeGoodQty;
    private BigDecimal afterGoodQty;
    private BigDecimal beforeDefectQty;
    private BigDecimal afterDefectQty;
    private LocalDateTime beforeProducedAt;
    private LocalDateTime afterProducedAt;
    private String beforeWorkerRef;
    private String afterWorkerRef;
    private String reason;
    private LocalDateTime createdAt;
    private String createdBy;
    public Long getCorrectionId() { return correctionId; }
    public void setCorrectionId(Long correctionId) { this.correctionId = correctionId; }
    public Long getProductionResultId() { return productionResultId; }
    public void setProductionResultId(Long productionResultId) { this.productionResultId = productionResultId; }
    public Long getFromRevision() { return fromRevision; }
    public void setFromRevision(Long fromRevision) { this.fromRevision = fromRevision; }
    public Long getToRevision() { return toRevision; }
    public void setToRevision(Long toRevision) { this.toRevision = toRevision; }
    public BigDecimal getBeforeProducedQty() { return beforeProducedQty; }
    public void setBeforeProducedQty(BigDecimal beforeProducedQty) { this.beforeProducedQty = beforeProducedQty; }
    public BigDecimal getAfterProducedQty() { return afterProducedQty; }
    public void setAfterProducedQty(BigDecimal afterProducedQty) { this.afterProducedQty = afterProducedQty; }
    public BigDecimal getBeforeGoodQty() { return beforeGoodQty; }
    public void setBeforeGoodQty(BigDecimal beforeGoodQty) { this.beforeGoodQty = beforeGoodQty; }
    public BigDecimal getAfterGoodQty() { return afterGoodQty; }
    public void setAfterGoodQty(BigDecimal afterGoodQty) { this.afterGoodQty = afterGoodQty; }
    public BigDecimal getBeforeDefectQty() { return beforeDefectQty; }
    public void setBeforeDefectQty(BigDecimal beforeDefectQty) { this.beforeDefectQty = beforeDefectQty; }
    public BigDecimal getAfterDefectQty() { return afterDefectQty; }
    public void setAfterDefectQty(BigDecimal afterDefectQty) { this.afterDefectQty = afterDefectQty; }
    public LocalDateTime getBeforeProducedAt() { return beforeProducedAt; }
    public void setBeforeProducedAt(LocalDateTime beforeProducedAt) { this.beforeProducedAt = beforeProducedAt; }
    public LocalDateTime getAfterProducedAt() { return afterProducedAt; }
    public void setAfterProducedAt(LocalDateTime afterProducedAt) { this.afterProducedAt = afterProducedAt; }
    public String getBeforeWorkerRef() { return beforeWorkerRef; }
    public void setBeforeWorkerRef(String beforeWorkerRef) { this.beforeWorkerRef = beforeWorkerRef; }
    public String getAfterWorkerRef() { return afterWorkerRef; }
    public void setAfterWorkerRef(String afterWorkerRef) { this.afterWorkerRef = afterWorkerRef; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}