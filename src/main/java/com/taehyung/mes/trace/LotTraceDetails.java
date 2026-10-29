package com.taehyung.mes.trace;

import com.taehyung.mes.bom.BomVersion;
import com.taehyung.mes.lot.MaterialConsumption;
import com.taehyung.mes.lot.MaterialConsumptionMaterial;
import com.taehyung.mes.lot.MaterialReturn;
import com.taehyung.mes.production.FinishedGoodsLot;
import com.taehyung.mes.production.ProductionResult;
import com.taehyung.mes.quality.QualityInspection;
import com.taehyung.mes.quality.QualityInspectionLot;
import com.taehyung.mes.routing.RoutingRevision;
import com.taehyung.mes.routing.RoutingStep;
import com.taehyung.mes.workorder.WorkOrder;
import com.taehyung.mes.workorder.WorkOrderTotals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class LotTraceDetails {
    private FinishedGoodsLot lot;
    private WorkOrder order;
    private BomVersion bom;
    private RoutingRevision routing;
    private List<RoutingStep> steps = List.of();
    private List<ProductionResult> results = List.of();
    private WorkOrderTotals totals;
    private List<MaterialUsage> usages = List.of();
    private List<MaterialLotTotal> materialLots = List.of();
    private List<MaterialConsumptionMaterial> materials = List.of();
    private List<MaterialConsumptionMaterial> comparisons = List.of();
    private QualityInspectionLot quality;
    private List<QualityInspection> inspections = List.of();
    private final List<String> warnings = new ArrayList<>();
    public FinishedGoodsLot getLot() { return lot; }
    public void setLot(FinishedGoodsLot lot) { this.lot = lot; }
    public WorkOrder getOrder() { return order; }
    public void setOrder(WorkOrder order) { this.order = order; }
    public BomVersion getBom() { return bom; }
    public void setBom(BomVersion bom) { this.bom = bom; }
    public RoutingRevision getRouting() { return routing; }
    public void setRouting(RoutingRevision routing) { this.routing = routing; }
    public List<RoutingStep> getSteps() { return steps; }
    public void setSteps(List<RoutingStep> steps) { this.steps = steps; }
    public List<ProductionResult> getResults() { return results; }
    public void setResults(List<ProductionResult> results) { this.results = results; }
    public WorkOrderTotals getTotals() { return totals; }
    public void setTotals(WorkOrderTotals totals) { this.totals = totals; }
    public List<MaterialUsage> getUsages() { return usages; }
    public void setUsages(List<MaterialUsage> usages) { this.usages = usages; }
    public List<MaterialLotTotal> getMaterialLots() { return materialLots; }
    public void setMaterialLots(List<MaterialLotTotal> materialLots) { this.materialLots = materialLots; }
    public List<MaterialConsumptionMaterial> getMaterials() { return materials; }
    public void setMaterials(List<MaterialConsumptionMaterial> materials) { this.materials = materials; }
    public List<MaterialConsumptionMaterial> getComparisons() { return comparisons; }
    public void setComparisons(List<MaterialConsumptionMaterial> comparisons) { this.comparisons = comparisons; }
    public QualityInspectionLot getQuality() { return quality; }
    public void setQuality(QualityInspectionLot quality) { this.quality = quality; }
    public List<QualityInspection> getInspections() { return inspections; }
    public void setInspections(List<QualityInspection> inspections) { this.inspections = inspections; }
    public List<String> getWarnings() { return warnings; }
    public String getProductionProgressDisplay() {
        if (order == null || order.getOrderedQty().signum() <= 0) { return "계산 불가"; }
        return totals.getProducedQty().multiply(new BigDecimal("100"))
                .divide(order.getOrderedQty(), 2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    public static class MaterialUsage {
        private final MaterialConsumption source;
        private final List<MaterialReturn> returns;
        private final BigDecimal returnedQty;
        public MaterialUsage(MaterialConsumption source, List<MaterialReturn> returns) {
            this.source = source; this.returns = returns;
            this.returnedQty = returns.stream().map(MaterialReturn::getReturnedQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        public MaterialConsumption getSource() { return source; }
        public List<MaterialReturn> getReturns() { return returns; }
        public BigDecimal getReturnedQty() { return returnedQty; }
        public BigDecimal getNetConsumedQty() { return source.getConsumedQty().subtract(returnedQty); }
    }
    public static class MaterialLotTotal {
        private final MaterialConsumption source;
        private BigDecimal consumedQty = BigDecimal.ZERO;
        private BigDecimal returnedQty = BigDecimal.ZERO;
        public MaterialLotTotal(MaterialConsumption source) { this.source = source; }
        public void add(MaterialUsage usage) {
            consumedQty = consumedQty.add(usage.getSource().getConsumedQty()); returnedQty = returnedQty.add(usage.getReturnedQty());
        }
        public MaterialConsumption getSource() { return source; }
        public BigDecimal getConsumedQty() { return consumedQty; }
        public BigDecimal getReturnedQty() { return returnedQty; }
        public BigDecimal getNetConsumedQty() { return consumedQty.subtract(returnedQty); }
    }
}
