package com.taehyung.mes.lot;

public class MaterialConsumptionMaterial {
    private Long bomComponentId;
    private Long materialItemId;
    private String materialCode;
    private String materialName;
    private String unitCode;
    private java.math.BigDecimal requiredQty;
    private java.math.BigDecimal materialRequiredQty;
    private java.math.BigDecimal baseQty;
    private java.math.BigDecimal orderedQty;
    private java.math.BigDecimal consumedQty;
    private java.math.BigDecimal returnedQty;
    public Long getBomComponentId() { return bomComponentId; }
    public void setBomComponentId(Long bomComponentId) { this.bomComponentId = bomComponentId; }
    public Long getMaterialItemId() { return materialItemId; }
    public void setMaterialItemId(Long materialItemId) { this.materialItemId = materialItemId; }
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }
    public String getUnitCode() { return unitCode; }
    public void setUnitCode(String unitCode) { this.unitCode = unitCode; }
    public java.math.BigDecimal getRequiredQty() { return requiredQty; }
    public void setRequiredQty(java.math.BigDecimal requiredQty) { this.requiredQty = requiredQty; }
    public java.math.BigDecimal getMaterialRequiredQty() { return materialRequiredQty; }
    public void setMaterialRequiredQty(java.math.BigDecimal materialRequiredQty) { this.materialRequiredQty = materialRequiredQty; }
    public java.math.BigDecimal getBaseQty() { return baseQty; }
    public void setBaseQty(java.math.BigDecimal baseQty) { this.baseQty = baseQty; }
    public java.math.BigDecimal getOrderedQty() { return orderedQty; }
    public void setOrderedQty(java.math.BigDecimal orderedQty) { this.orderedQty = orderedQty; }
    public java.math.BigDecimal getConsumedQty() { return consumedQty; }
    public void setConsumedQty(java.math.BigDecimal consumedQty) { this.consumedQty = consumedQty; }
    public java.math.BigDecimal getReturnedQty() { return returnedQty; }
    public void setReturnedQty(java.math.BigDecimal returnedQty) { this.returnedQty = returnedQty; }
    public java.math.BigDecimal getNetConsumedQty() { return consumedQty.subtract(returnedQty); }
    public String getExpectedQtyDisplay() {
        return exact(orderedQty.multiply(materialRequiredQty));
    }
    public String getDifferenceDisplay() {
        return exact(getNetConsumedQty().multiply(baseQty).subtract(orderedQty.multiply(materialRequiredQty)));
    }
    private String exact(java.math.BigDecimal numerator) {
        if (baseQty.signum() <= 0) { throw new IllegalStateException("BOM base quantity must be positive."); }
        try { return numerator.divide(baseQty).stripTrailingZeros().toPlainString(); }
        catch (ArithmeticException exception) { return numerator.stripTrailingZeros().toPlainString() + " / " + baseQty.stripTrailingZeros().toPlainString(); }
    }
}
