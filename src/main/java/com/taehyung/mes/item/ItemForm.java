package com.taehyung.mes.item;

/** 작성자·PK·시각은 요청에서 받지 않는다. */
public class ItemForm {
    private String itemCode;
    private String itemName;
    private String itemKind;
    private String unitCode;
    private Integer isActive;

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

    public static ItemForm from(Item item) {
        ItemForm form = new ItemForm();
        form.setItemCode(item.getItemCode());
        form.setItemName(item.getItemName());
        form.setItemKind(item.getItemKind());
        form.setUnitCode(item.getUnitCode());
        form.setIsActive(item.getIsActive());
        return form;
    }
}
