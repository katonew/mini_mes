package com.taehyung.mes.workorder;

public class WorkOrderBomOption {
    private Long bomVersionId;
    private String bomCode;
    private Long versionNo;
    private java.math.BigDecimal baseQty;
    private String productUnitCode;
    private Long componentCount;
    private String unavailableReason;
    public Long getBomVersionId() { return bomVersionId; }
    public void setBomVersionId(Long bomVersionId) { this.bomVersionId = bomVersionId; }
    public String getBomCode() { return bomCode; }
    public void setBomCode(String bomCode) { this.bomCode = bomCode; }
    public Long getVersionNo() { return versionNo; }
    public void setVersionNo(Long versionNo) { this.versionNo = versionNo; }
    public java.math.BigDecimal getBaseQty() { return baseQty; }
    public void setBaseQty(java.math.BigDecimal baseQty) { this.baseQty = baseQty; }
    public String getProductUnitCode() { return productUnitCode; }
    public void setProductUnitCode(String productUnitCode) { this.productUnitCode = productUnitCode; }
    public Long getComponentCount() { return componentCount; }
    public void setComponentCount(Long componentCount) { this.componentCount = componentCount; }
    public String getUnavailableReason() { return unavailableReason; }
    public void setUnavailableReason(String unavailableReason) { this.unavailableReason = unavailableReason; }
    public boolean isSelectable() { return unavailableReason == null; }
}
