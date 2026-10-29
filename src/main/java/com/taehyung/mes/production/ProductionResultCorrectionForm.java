package com.taehyung.mes.production;

import java.math.BigDecimal;

public class ProductionResultCorrectionForm {
    private Long expectedRevision;
    private BigDecimal producedQty;
    private BigDecimal goodQty;
    private BigDecimal defectQty;
    private String reason;
    public Long getExpectedRevision() { return expectedRevision; }
    public void setExpectedRevision(Long expectedRevision) { this.expectedRevision = expectedRevision; }
    public BigDecimal getProducedQty() { return producedQty; }
    public void setProducedQty(BigDecimal producedQty) { this.producedQty = producedQty; }
    public BigDecimal getGoodQty() { return goodQty; }
    public void setGoodQty(BigDecimal goodQty) { this.goodQty = goodQty; }
    public BigDecimal getDefectQty() { return defectQty; }
    public void setDefectQty(BigDecimal defectQty) { this.defectQty = defectQty; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
