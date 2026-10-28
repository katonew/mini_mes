package com.taehyung.mes.lot;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RawMaterialLot {
    private Long rawLotId;
    private Long itemId;
    private String lotNo;
    private String unitCode;
    private BigDecimal registeredQty;
    private BigDecimal availableQty;
    private LocalDateTime registeredAt;
    private LocalDateTime createdAt;
    private String createdBy;
    private String itemCode;
    private String itemName;
    private String itemKind;
    // 소비·반환 집계는 상세 조회에서만 같은 SQL 시점으로 채운다.
    private BigDecimal consumedQty;
    private BigDecimal returnedQty;

    public Long getRawLotId() { return rawLotId; }
    public void setRawLotId(Long rawLotId) { this.rawLotId = rawLotId; }
    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public String getLotNo() { return lotNo; }
    public void setLotNo(String lotNo) { this.lotNo = lotNo; }
    public String getUnitCode() { return unitCode; }
    public void setUnitCode(String unitCode) { this.unitCode = unitCode; }
    public BigDecimal getRegisteredQty() { return registeredQty; }
    public void setRegisteredQty(BigDecimal registeredQty) { this.registeredQty = registeredQty; }
    public BigDecimal getAvailableQty() { return availableQty; }
    public void setAvailableQty(BigDecimal availableQty) { this.availableQty = availableQty; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getItemKind() { return itemKind; }
    public void setItemKind(String itemKind) { this.itemKind = itemKind; }
    public BigDecimal getConsumedQty() { return consumedQty; }
    public void setConsumedQty(BigDecimal consumedQty) { this.consumedQty = consumedQty; }
    public BigDecimal getReturnedQty() { return returnedQty; }
    public void setReturnedQty(BigDecimal returnedQty) { this.returnedQty = returnedQty; }
    public BigDecimal getNetConsumedQty() { return consumedQty.subtract(returnedQty); }
    public boolean isQuantityConsistent() {
        return returnedQty.signum() >= 0 && consumedQty.compareTo(returnedQty) >= 0
                && availableQty.compareTo(registeredQty.subtract(getNetConsumedQty())) == 0;
    }
}
