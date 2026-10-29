package com.taehyung.mes.lot;

public class MaterialReturnForm {
    private Long consumptionId;
    private java.math.BigDecimal returnedQty;
    private String reason;
    private String requestKey;
    public Long getConsumptionId() { return consumptionId; }
    public void setConsumptionId(Long consumptionId) { this.consumptionId = consumptionId; }
    public java.math.BigDecimal getReturnedQty() { return returnedQty; }
    public void setReturnedQty(java.math.BigDecimal returnedQty) { this.returnedQty = returnedQty; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
}
