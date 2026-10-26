package com.taehyung.mes.routing;

public class RoutingStep {
    private Long routingStepId;
    private Long routingRevisionId;
    private Long processId;
    private Long stepNo;
    private String processNameSnapshot;
    private String processCode;
    private String currentProcessName;
    private Integer isActive;
    private String createdBy;
    public Long getRoutingStepId() { return routingStepId; }
    public void setRoutingStepId(Long routingStepId) { this.routingStepId = routingStepId; }
    public Long getRoutingRevisionId() { return routingRevisionId; }
    public void setRoutingRevisionId(Long routingRevisionId) { this.routingRevisionId = routingRevisionId; }
    public Long getProcessId() { return processId; }
    public void setProcessId(Long processId) { this.processId = processId; }
    public Long getStepNo() { return stepNo; }
    public void setStepNo(Long stepNo) { this.stepNo = stepNo; }
    public String getProcessNameSnapshot() { return processNameSnapshot; }
    public void setProcessNameSnapshot(String processNameSnapshot) { this.processNameSnapshot = processNameSnapshot; }
    public String getProcessCode() { return processCode; }
    public void setProcessCode(String processCode) { this.processCode = processCode; }
    public String getCurrentProcessName() { return currentProcessName; }
    public void setCurrentProcessName(String currentProcessName) { this.currentProcessName = currentProcessName; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
