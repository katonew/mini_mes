package com.taehyung.mes.routing;

public class Routing {
    private Long routingId;
    private String routingCode;
    private Long productItemId;
    private String productCode;
    private String productName;
    private String createdBy;
    public Long getRoutingId() { return routingId; }
    public void setRoutingId(Long routingId) { this.routingId = routingId; }
    public String getRoutingCode() { return routingCode; }
    public void setRoutingCode(String routingCode) { this.routingCode = routingCode; }
    public Long getProductItemId() { return productItemId; }
    public void setProductItemId(Long productItemId) { this.productItemId = productItemId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
