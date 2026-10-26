package com.taehyung.mes.bom;

import java.math.BigDecimal;

public class BomComponentForm {
    private Long bomComponentId;
    private Long materialItemId;
    private BigDecimal requiredQty;

    public Long getBomComponentId() { return bomComponentId; }
    public void setBomComponentId(Long bomComponentId) { this.bomComponentId = bomComponentId; }
    public Long getMaterialItemId() { return materialItemId; }
    public void setMaterialItemId(Long materialItemId) { this.materialItemId = materialItemId; }
    public BigDecimal getRequiredQty() { return requiredQty; }
    public void setRequiredQty(BigDecimal requiredQty) { this.requiredQty = requiredQty; }
}

