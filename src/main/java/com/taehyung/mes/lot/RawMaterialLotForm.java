package com.taehyung.mes.lot;

import java.math.BigDecimal;

public class RawMaterialLotForm {
    private Long itemId;
    private String lotNo;
    private BigDecimal registeredQty;
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getLotNo() { return lotNo; }
    public void setLotNo(String lotNo) { this.lotNo = lotNo; }
    public BigDecimal getRegisteredQty() { return registeredQty; }
    public void setRegisteredQty(BigDecimal registeredQty) { this.registeredQty = registeredQty; }
}
