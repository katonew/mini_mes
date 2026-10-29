package com.taehyung.mes.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DashboardSummary {
    private Long workOrderCount;
    private Long readyCount;
    private Long inProgressCount;
    private Long completedCount;
    private Long cancelledCount;
    private BigDecimal producedQty;
    private BigDecimal goodQty;
    private BigDecimal defectQty;
    private Long excludedResultCount;
    private BigDecimal inspectedQty;
    private BigDecimal acceptedQty;
    private BigDecimal rejectedQty;
    private Long invalidInspectionCount;
    private BigDecimal inspectionTargetQty;
    private BigDecimal uninspectedQty;
    private Long invalidTargetCount;
    private Long planCount;
    private BigDecimal plannedQty;
    private Long rawLotCount;
    private Long exhaustedLotCount;
    public Long getWorkOrderCount() { return workOrderCount; }
    public void setWorkOrderCount(Long workOrderCount) { this.workOrderCount = workOrderCount; }
    public Long getReadyCount() { return readyCount; }
    public void setReadyCount(Long readyCount) { this.readyCount = readyCount; }
    public Long getInProgressCount() { return inProgressCount; }
    public void setInProgressCount(Long inProgressCount) { this.inProgressCount = inProgressCount; }
    public Long getCompletedCount() { return completedCount; }
    public void setCompletedCount(Long completedCount) { this.completedCount = completedCount; }
    public Long getCancelledCount() { return cancelledCount; }
    public void setCancelledCount(Long cancelledCount) { this.cancelledCount = cancelledCount; }
    public BigDecimal getProducedQty() { return producedQty; }
    public void setProducedQty(BigDecimal producedQty) { this.producedQty = producedQty; }
    public BigDecimal getGoodQty() { return goodQty; }
    public void setGoodQty(BigDecimal goodQty) { this.goodQty = goodQty; }
    public BigDecimal getDefectQty() { return defectQty; }
    public void setDefectQty(BigDecimal defectQty) { this.defectQty = defectQty; }
    public Long getExcludedResultCount() { return excludedResultCount; }
    public void setExcludedResultCount(Long excludedResultCount) { this.excludedResultCount = excludedResultCount; }
    public BigDecimal getInspectedQty() { return inspectedQty; }
    public void setInspectedQty(BigDecimal inspectedQty) { this.inspectedQty = inspectedQty; }
    public BigDecimal getAcceptedQty() { return acceptedQty; }
    public void setAcceptedQty(BigDecimal acceptedQty) { this.acceptedQty = acceptedQty; }
    public BigDecimal getRejectedQty() { return rejectedQty; }
    public void setRejectedQty(BigDecimal rejectedQty) { this.rejectedQty = rejectedQty; }
    public Long getInvalidInspectionCount() { return invalidInspectionCount; }
    public void setInvalidInspectionCount(Long invalidInspectionCount) { this.invalidInspectionCount = invalidInspectionCount; }
    public BigDecimal getInspectionTargetQty() { return inspectionTargetQty; }
    public void setInspectionTargetQty(BigDecimal inspectionTargetQty) { this.inspectionTargetQty = inspectionTargetQty; }
    public BigDecimal getUninspectedQty() { return uninspectedQty; }
    public void setUninspectedQty(BigDecimal uninspectedQty) { this.uninspectedQty = uninspectedQty; }
    public Long getInvalidTargetCount() { return invalidTargetCount; }
    public void setInvalidTargetCount(Long invalidTargetCount) { this.invalidTargetCount = invalidTargetCount; }
    public Long getPlanCount() { return planCount; }
    public void setPlanCount(Long planCount) { this.planCount = planCount; }
    public BigDecimal getPlannedQty() { return plannedQty; }
    public void setPlannedQty(BigDecimal plannedQty) { this.plannedQty = plannedQty; }
    public Long getRawLotCount() { return rawLotCount; }
    public void setRawLotCount(Long rawLotCount) { this.rawLotCount = rawLotCount; }
    public Long getExhaustedLotCount() { return exhaustedLotCount; }
    public void setExhaustedLotCount(Long exhaustedLotCount) { this.exhaustedLotCount = exhaustedLotCount; }
    public String getProductionYieldDisplay() { return rate(goodQty, producedQty); }
    public String getInspectionYieldDisplay() { return rate(acceptedQty, inspectedQty); }
    public String getUninspectedQtyDisplay() {
        return invalidTargetCount > 0 ? "정합성 확인 필요" : uninspectedQty.stripTrailingZeros().toPlainString() + " EA";
    }
    private String rate(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.signum() == 0) { return "집계 대상 없음"; }
        return numerator.multiply(new BigDecimal("100"))
                .divide(denominator, 2, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
