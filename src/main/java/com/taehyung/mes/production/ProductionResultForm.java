package com.taehyung.mes.production;

import java.math.BigDecimal;

public class ProductionResultForm {
    private Long workOrderId;
    private String lotNo;
    private BigDecimal producedQty;
    private BigDecimal goodQty;
    private BigDecimal defectQty;
    private String workerRef;
    private String requestKey;
    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public String getLotNo() { return lotNo; }
    public void setLotNo(String lotNo) { this.lotNo = lotNo; }
    public BigDecimal getProducedQty() { return producedQty; }
    public void setProducedQty(BigDecimal producedQty) { this.producedQty = producedQty; }
    public BigDecimal getGoodQty() { return goodQty; }
    public void setGoodQty(BigDecimal goodQty) { this.goodQty = goodQty; }
    public BigDecimal getDefectQty() { return defectQty; }
    public void setDefectQty(BigDecimal defectQty) { this.defectQty = defectQty; }
    public String getWorkerRef() { return workerRef; }
    public void setWorkerRef(String workerRef) { this.workerRef = workerRef; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
}
