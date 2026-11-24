package com.taehyung.mes.trace;

import com.taehyung.mes.workorder.WorkOrder;
import com.taehyung.mes.production.FinishedGoodsLot;
import com.taehyung.mes.quality.QualityInspectionLot;
import java.math.BigDecimal;

/** 작업지시 수준의 투입 관계이며 개별 제품에 원자재를 배분하지 않는다. */
public class ForwardLotTraceOrder {
    private WorkOrder order;
    private FinishedGoodsLot lot;
    private QualityInspectionLot quality;
    private BigDecimal consumedQty;
    private BigDecimal returnedQty;
    public WorkOrder getOrder() { return order; }
    public void setOrder(WorkOrder order) { this.order = order; }
    public FinishedGoodsLot getLot() { return lot; }
    public void setLot(FinishedGoodsLot lot) { this.lot = lot; }
    public QualityInspectionLot getQuality() { return quality; }
    public void setQuality(QualityInspectionLot quality) { this.quality = quality; }
    public BigDecimal getConsumedQty() { return consumedQty; }
    public void setConsumedQty(BigDecimal consumedQty) { this.consumedQty = consumedQty; }
    public BigDecimal getReturnedQty() { return returnedQty; }
    public void setReturnedQty(BigDecimal returnedQty) { this.returnedQty = returnedQty; }
    public BigDecimal getNetConsumedQty() { return consumedQty.subtract(returnedQty); }
    public boolean isFullyReturned() { return getNetConsumedQty().signum() == 0; }
    public String getInspectionProgressLabel() {
        if (order.getInspectionTargetQty() == null) { return "완료 시 확정 · 검사 미실시"; }
        if (order.getInspectionTargetQty().signum() == 0) { return "검사 대상 0"; }
        if (quality == null || quality.getLastRoundNo() == 0) { return "검사 미실시"; }
        return quality.getUninspectedQty().signum() > 0 ? "일부 검사" : "검사 완료";
    }
}
