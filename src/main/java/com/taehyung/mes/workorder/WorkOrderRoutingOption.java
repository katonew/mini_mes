package com.taehyung.mes.workorder;

public class WorkOrderRoutingOption {
    private Long routingRevisionId;
    private String routingCode;
    private Long revisionNo;
    private Long stepCount;
    private Long inactiveProcessCount;
    public Long getRoutingRevisionId() { return routingRevisionId; }
    public void setRoutingRevisionId(Long routingRevisionId) { this.routingRevisionId = routingRevisionId; }
    public String getRoutingCode() { return routingCode; }
    public void setRoutingCode(String routingCode) { this.routingCode = routingCode; }
    public Long getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Long revisionNo) { this.revisionNo = revisionNo; }
    public Long getStepCount() { return stepCount; }
    public void setStepCount(Long stepCount) { this.stepCount = stepCount; }
    public Long getInactiveProcessCount() { return inactiveProcessCount; }
    public void setInactiveProcessCount(Long inactiveProcessCount) { this.inactiveProcessCount = inactiveProcessCount; }
    public boolean isSelectable() { return stepCount > 0 && inactiveProcessCount == 0; }
    public String getUnavailableReason() { return stepCount == 0 ? "공정 구성 없음" : (inactiveProcessCount > 0 ? "미사용 공정 " + inactiveProcessCount + "개" : ""); }
}
