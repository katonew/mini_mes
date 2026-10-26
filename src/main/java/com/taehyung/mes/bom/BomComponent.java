package com.taehyung.mes.bom;

import java.math.BigDecimal;

public class BomComponent {
    private Long bomComponentId;
    private Long bomVersionId;
    private Long materialItemId;
    private Long lineNo;
    private BigDecimal requiredQty;
    private String materialUnitCode;
    private String materialCode;
    private String materialName;
    private Integer isActive;
    private String createdBy;

    public Long getBomComponentId() { return bomComponentId; }
    public void setBomComponentId(Long bomComponentId) { this.bomComponentId = bomComponentId; }
    public Long getBomVersionId() { return bomVersionId; }
    public void setBomVersionId(Long bomVersionId) { this.bomVersionId = bomVersionId; }
    public Long getMaterialItemId() { return materialItemId; }
    public void setMaterialItemId(Long materialItemId) { this.materialItemId = materialItemId; }
    public Long getLineNo() { return lineNo; }
    public void setLineNo(Long lineNo) { this.lineNo = lineNo; }
    public BigDecimal getRequiredQty() { return requiredQty; }
    public void setRequiredQty(BigDecimal requiredQty) { this.requiredQty = requiredQty; }
    public String getMaterialUnitCode() { return materialUnitCode; }
    public void setMaterialUnitCode(String materialUnitCode) { this.materialUnitCode = materialUnitCode; }
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}

