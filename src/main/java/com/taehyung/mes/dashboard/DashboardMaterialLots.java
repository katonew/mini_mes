package com.taehyung.mes.dashboard;


public class DashboardMaterialLots {
    private Long itemId;
    private String itemCode;
    private String itemName;
    private String unitCode;
    private Long lotCount;
    private Long exhaustedCount;
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getUnitCode() { return unitCode; }
    public void setUnitCode(String unitCode) { this.unitCode = unitCode; }
    public Long getLotCount() { return lotCount; }
    public void setLotCount(Long lotCount) { this.lotCount = lotCount; }
    public Long getExhaustedCount() { return exhaustedCount; }
    public void setExhaustedCount(Long exhaustedCount) { this.exhaustedCount = exhaustedCount; }
}
