package com.taehyung.mes.workorder;

public class WorkOrderForm {
    private Long productionPlanId;
    private Long productItemId;
    private Long bomVersionId;
    private Long routingRevisionId;
    private java.math.BigDecimal orderedQty;
    public Long getProductionPlanId() { return productionPlanId; }
    public void setProductionPlanId(Long productionPlanId) { this.productionPlanId = productionPlanId; }
    public Long getProductItemId() { return productItemId; }
    public void setProductItemId(Long productItemId) { this.productItemId = productItemId; }
    public Long getBomVersionId() { return bomVersionId; }
    public void setBomVersionId(Long bomVersionId) { this.bomVersionId = bomVersionId; }
    public Long getRoutingRevisionId() { return routingRevisionId; }
    public void setRoutingRevisionId(Long routingRevisionId) { this.routingRevisionId = routingRevisionId; }
    public java.math.BigDecimal getOrderedQty() { return orderedQty; }
    public void setOrderedQty(java.math.BigDecimal orderedQty) { this.orderedQty = orderedQty; }
}
