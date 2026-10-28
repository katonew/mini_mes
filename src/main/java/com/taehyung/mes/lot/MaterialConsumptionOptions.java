package com.taehyung.mes.lot;

public class MaterialConsumptionOptions {
    private com.taehyung.mes.workorder.WorkOrder order;
    private java.util.List<MaterialConsumptionMaterial> materials;
    private java.util.List<RawMaterialLot> lots;
    public com.taehyung.mes.workorder.WorkOrder getOrder() { return order; }
    public void setOrder(com.taehyung.mes.workorder.WorkOrder order) { this.order = order; }
    public java.util.List<MaterialConsumptionMaterial> getMaterials() { return materials; }
    public void setMaterials(java.util.List<MaterialConsumptionMaterial> materials) { this.materials = materials; }
    public java.util.List<RawMaterialLot> getLots() { return lots; }
    public void setLots(java.util.List<RawMaterialLot> lots) { this.lots = lots; }
}
