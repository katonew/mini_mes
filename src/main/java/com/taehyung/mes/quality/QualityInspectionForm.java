package com.taehyung.mes.quality;

import java.math.BigDecimal;

public class QualityInspectionForm {
    private Long finishedLotId;
    private BigDecimal inspectedQty;
    private BigDecimal acceptedQty;
    private BigDecimal rejectedQty;
    private String inspectorRef;
    private String judgementReason;
    private String requestKey;
    public Long getFinishedLotId() { return finishedLotId; }
    public void setFinishedLotId(Long finishedLotId) { this.finishedLotId = finishedLotId; }
    public BigDecimal getInspectedQty() { return inspectedQty; }
    public void setInspectedQty(BigDecimal inspectedQty) { this.inspectedQty = inspectedQty; }
    public BigDecimal getAcceptedQty() { return acceptedQty; }
    public void setAcceptedQty(BigDecimal acceptedQty) { this.acceptedQty = acceptedQty; }
    public BigDecimal getRejectedQty() { return rejectedQty; }
    public void setRejectedQty(BigDecimal rejectedQty) { this.rejectedQty = rejectedQty; }
    public String getInspectorRef() { return inspectorRef; }
    public void setInspectorRef(String inspectorRef) { this.inspectorRef = inspectorRef; }
    public String getJudgementReason() { return judgementReason; }
    public void setJudgementReason(String judgementReason) { this.judgementReason = judgementReason; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
}
