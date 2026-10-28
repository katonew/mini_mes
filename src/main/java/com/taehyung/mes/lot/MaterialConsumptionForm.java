package com.taehyung.mes.lot;

public class MaterialConsumptionForm {
    private Long workOrderId;
    private Long bomComponentId;
    private Long rawLotId;
    private java.math.BigDecimal consumedQty;
    private String requestKey;
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public Long getBomComponentId() { return bomComponentId; }
    public void setBomComponentId(Long bomComponentId) { this.bomComponentId = bomComponentId; }
    public Long getRawLotId() { return rawLotId; }
    public void setRawLotId(Long rawLotId) { this.rawLotId = rawLotId; }
    public java.math.BigDecimal getConsumedQty() { return consumedQty; }
    public void setConsumedQty(java.math.BigDecimal consumedQty) { this.consumedQty = consumedQty; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
}
