package com.taehyung.mes.routing;

public class RoutingForm {
    private String routingCode;
    private Long productItemId;
    private Long revisionNo;
    private java.util.List<RoutingStepForm> steps = new java.util.ArrayList<>();
    public String getRoutingCode() { return routingCode; }
    public void setRoutingCode(String routingCode) { this.routingCode = routingCode; }
    public Long getProductItemId() { return productItemId; }
    public void setProductItemId(Long productItemId) { this.productItemId = productItemId; }
    public Long getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Long revisionNo) { this.revisionNo = revisionNo; }
    public java.util.List<RoutingStepForm> getSteps() { return steps; }
    public void setSteps(java.util.List<RoutingStepForm> steps) { this.steps = steps; }
}
