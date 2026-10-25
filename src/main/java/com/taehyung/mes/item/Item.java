package com.taehyung.mes.item;

import java.time.LocalDateTime;

/** item 테이블 행 모델. HTTP 폼 바인딩에 사용하지 않는다. */
public class Item {
    private Long itemId;
    private String itemCode;
    private String itemName;
    private String itemKind;
    private String unitCode;
    private Integer isActive;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getItemKind() { return itemKind; }
    public void setItemKind(String itemKind) { this.itemKind = itemKind; }
    public String getUnitCode() { return unitCode; }
    public void setUnitCode(String unitCode) { this.unitCode = unitCode; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getItemKindLabel() { return ItemKind.labelOf(itemKind); }
    public String getUnitLabel() { return ItemUnit.labelOf(unitCode); }
}
