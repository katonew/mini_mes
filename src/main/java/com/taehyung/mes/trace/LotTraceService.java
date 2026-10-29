package com.taehyung.mes.trace;

import com.taehyung.mes.bom.BomMapper;
import com.taehyung.mes.lot.MaterialConsumptionMapper;
import com.taehyung.mes.lot.MaterialConsumptionMaterial;
import com.taehyung.mes.lot.MaterialReturn;
import com.taehyung.mes.lot.MaterialReturnMapper;
import com.taehyung.mes.production.ProductionResultMapper;
import com.taehyung.mes.production.ProductionResultNotFoundException;
import com.taehyung.mes.quality.QualityInspectionLot;
import com.taehyung.mes.quality.QualityInspectionMapper;
import com.taehyung.mes.routing.RoutingMapper;
import com.taehyung.mes.workorder.WorkOrderMapper;
import com.taehyung.mes.workorder.WorkOrderTotals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LotTraceService {
    private final ProductionResultMapper production;
    private final WorkOrderMapper orders;
    private final MaterialConsumptionMapper consumption;
    private final MaterialReturnMapper returns;
    private final QualityInspectionMapper quality;
    private final BomMapper boms;
    private final RoutingMapper routings;
    public LotTraceService(ProductionResultMapper production, WorkOrderMapper orders, MaterialConsumptionMapper consumption,
                           MaterialReturnMapper returns, QualityInspectionMapper quality, BomMapper boms, RoutingMapper routings) {
        this.production = production; this.orders = orders; this.consumption = consumption;
        this.returns = returns; this.quality = quality; this.boms = boms; this.routings = routings;
    }
    // 모든 일반 SELECT가 동일한 InnoDB 일관 읽기 범위를 공유한다. 잠금·쓰기·별도 트랜잭션 호출은 없다.
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public LotTraceDetails get(Long lotId) {
        if (lotId == null || lotId <= 0) { throw new ProductionResultNotFoundException(); }
        var lot = production.findLotById(lotId);
        if (lot == null) { throw new ProductionResultNotFoundException(); }
        var trace = new LotTraceDetails(); trace.setLot(lot);
        var order = orders.findById(lot.getWorkOrderId()); trace.setOrder(order);
        if (order == null) { trace.getWarnings().add("연결된 작업지시 기준정보를 조회하지 못했습니다. BOM 예상량 비교를 제공할 수 없습니다."); }
        else {
            trace.setBom(boms.findVersion(order.getBomVersionId()));
            trace.setRouting(routings.findRevision(order.getRoutingRevisionId()));
            trace.setSteps(routings.findSteps(order.getRoutingRevisionId()));
            trace.setMaterials(consumption.findMaterials(order.getWorkOrderId()));
            if (trace.getBom() == null || !Objects.equals(trace.getBom().getProductItemId(), order.getProductItemId())) {
                trace.getWarnings().add("확정 BOM 버전과 작업지시 제품의 연결을 확인해 주세요.");
            }
            if (trace.getRouting() == null || !Objects.equals(trace.getRouting().getProductItemId(), order.getProductItemId()) || trace.getSteps().isEmpty()) {
                trace.getWarnings().add("확정 Routing 개정의 제품 연결 또는 공정 기록을 확인해 주세요.");
            }
            if (trace.getMaterials().isEmpty()) { trace.getWarnings().add("확정 BOM 구성 기록이 없습니다. 예상량 비교를 제공할 수 없습니다."); }
        }
        trace.setResults(production.findByLot(lotId));
        trace.setInspections(quality.findAll(null, lotId, "", ""));
        productionTotals(trace);
        materialHistory(trace);
        comparison(trace);
        inspectionTotals(trace);
        return trace;
    }
    private void productionTotals(LotTraceDetails trace) {
        var totals = new WorkOrderTotals(); totals.setResultCount((long) trace.getResults().size());
        BigDecimal produced = BigDecimal.ZERO, good = BigDecimal.ZERO, defect = BigDecimal.ZERO;
        for (var row : trace.getResults()) {
            produced = produced.add(row.getProducedQty()); good = good.add(row.getGoodQty()); defect = defect.add(row.getDefectQty());
            if (row.getProducedQty().compareTo(row.getGoodQty().add(row.getDefectQty())) != 0
                    || row.getProducedQty().signum() <= 0 || row.getGoodQty().signum() < 0 || row.getDefectQty().signum() < 0) {
                trace.getWarnings().add("생산실적 " + row.getProductionResultId() + "의 수량 정합성을 확인해 주세요.");
            }
        }
        totals.setProducedQty(produced); totals.setGoodQty(good); totals.setDefectQty(defect); trace.setTotals(totals);
        if (trace.getResults().isEmpty()) { trace.getWarnings().add("완제품 LOT에 연결된 생산실적 기록이 없습니다."); }
        var order = trace.getOrder();
        if (order != null && (produced.compareTo(order.getOrderedQty()) > 0
                || ("COMPLETED".equals(order.getStatus()) && (produced.signum() <= 0 || produced.compareTo(order.getOrderedQty()) != 0)))) {
            trace.getWarnings().add("누적 생산수량과 작업지시 수량·완료 상태가 일치하지 않습니다.");
        }
        var lot = trace.getLot();
        if (produced.compareTo(lot.getProducedQty()) != 0 || good.compareTo(lot.getGoodQty()) != 0 || defect.compareTo(lot.getDefectQty()) != 0
                || !Objects.equals(totals.getResultCount(), lot.getResultCount())) {
            trace.getWarnings().add("LOT 생산 집계와 조회된 실적 이력의 수량 또는 건수가 일치하지 않습니다.");
        }
    }
    private void materialHistory(LotTraceDetails trace) {
        Long orderId = trace.getLot().getWorkOrderId();
        var sources = consumption.findAll(orderId, "", "");
        var returnRows = returns.findAll(orderId, null, "", "");
        Map<Long, List<MaterialReturn>> bySource = returnRows.stream().collect(Collectors.groupingBy(MaterialReturn::getConsumptionId));
        var usages = new ArrayList<LotTraceDetails.MaterialUsage>();
        var lotTotals = new LinkedHashMap<MaterialLotKey, LotTraceDetails.MaterialLotTotal>();
        Map<Long, MaterialConsumptionMaterial> components = trace.getMaterials().stream()
                .collect(Collectors.toMap(MaterialConsumptionMaterial::getBomComponentId, Function.identity()));
        for (var source : sources) {
            var linked = bySource.remove(source.getConsumptionId());
            var usage = new LotTraceDetails.MaterialUsage(source, linked == null ? List.of() : linked); usages.add(usage);
            if (source.getConsumedQty().signum() <= 0 || usage.getNetConsumedQty().signum() < 0
                    || usage.getReturnedQty().compareTo(source.getReturnedQty()) != 0
                    || usage.getReturns().stream().anyMatch(row -> row.getReturnedQty().signum() <= 0)) {
                trace.getWarnings().add("원소비 " + source.getConsumptionId() + "의 소비·반환 수량이 일치하지 않습니다.");
            }
            var component = components.get(source.getBomComponentId());
            if (component == null || !Objects.equals(source.getItemCode(), component.getMaterialCode())
                    || !Objects.equals(source.getUnitCode(), component.getUnitCode())) {
                trace.getWarnings().add("원소비 " + source.getConsumptionId() + "의 원자재·단위가 확정 BOM 구성과 일치하지 않습니다.");
            }
            var key = new MaterialLotKey(source.getRawLotId(), source.getUnitCode());
            lotTotals.computeIfAbsent(key, ignored -> new LotTraceDetails.MaterialLotTotal(source)).add(usage);
        }
        if (!bySource.isEmpty()) { trace.getWarnings().add("조회된 원소비와 연결할 수 없는 반환 기록이 있습니다. 반환 이력 상세에서 확인해 주세요."); }
        trace.setUsages(usages); trace.setMaterialLots(new ArrayList<>(lotTotals.values()));
    }
    private void comparison(LotTraceDetails trace) {
        var order = trace.getOrder(); var bom = trace.getBom();
        if (order == null || bom == null) { return; }
        if (!Objects.equals(bom.getProductItemId(), order.getProductItemId()) || !Objects.equals(bom.getProductUnitCode(), order.getProductUnitCode())) {
            trace.getWarnings().add("BOM·작업지시 제품 또는 단위가 달라 예상량 비교를 제공할 수 없습니다. 단위 변환을 적용하지 않습니다."); return;
        }
        var comparisons = new LinkedHashMap<MaterialKey, MaterialConsumptionMaterial>();
        for (var row : trace.getMaterials()) {
            if (row.getBaseQty().signum() <= 0 || row.getMaterialRequiredQty().signum() <= 0
                    || row.getBaseQty().compareTo(bom.getBaseQty()) != 0 || row.getOrderedQty().compareTo(order.getOrderedQty()) != 0) {
                trace.getWarnings().add("BOM 구성 " + row.getBomComponentId() + "의 기준수량이 유효하지 않아 예상량을 계산할 수 없습니다."); continue;
            }
            comparisons.putIfAbsent(new MaterialKey(row.getMaterialItemId(), row.getUnitCode()), row);
        }
        trace.setComparisons(new ArrayList<>(comparisons.values()));
    }
    private void inspectionTotals(LotTraceDetails trace) {
        var lot = trace.getLot(); var order = trace.getOrder();
        var summary = new QualityInspectionLot(); summary.setFinishedLotId(lot.getFinishedLotId()); summary.setWorkOrderId(lot.getWorkOrderId());
        summary.setLotNo(lot.getLotNo()); summary.setProductCode(lot.getProductCode()); summary.setProductName(lot.getProductName());
        summary.setWorkOrderStatus(lot.getWorkOrderStatus());
        if (order != null) { summary.setInspectionTargetQty(order.getInspectionTargetQty()); summary.setProductUnitCode(order.getProductUnitCode()); }
        BigDecimal inspected = BigDecimal.ZERO, accepted = BigDecimal.ZERO, rejected = BigDecimal.ZERO;
        for (var row : trace.getInspections()) {
            inspected = inspected.add(row.getInspectedQty()); accepted = accepted.add(row.getAcceptedQty()); rejected = rejected.add(row.getRejectedQty());
            if (row.getInspectedQty().signum() <= 0 || row.getAcceptedQty().signum() < 0 || row.getRejectedQty().signum() < 0
                    || row.getInspectedQty().compareTo(row.getAcceptedQty().add(row.getRejectedQty())) != 0) {
                trace.getWarnings().add("품질검사 " + row.getInspectionId() + "의 검사·합격·불합격 수량이 일치하지 않습니다.");
            }
        }
        summary.setInspectedQty(inspected); summary.setAcceptedQty(accepted); summary.setRejectedQty(rejected); trace.setQuality(summary);
        BigDecimal target = summary.getInspectionTargetQty();
        if (("COMPLETED".equals(lot.getWorkOrderStatus()) && (target == null || target.compareTo(trace.getTotals().getGoodQty()) != 0))
                || (target != null && (target.signum() < 0 || inspected.compareTo(target) > 0 || summary.getUninspectedQty().signum() < 0))
                || (!trace.getInspections().isEmpty() && !"COMPLETED".equals(lot.getWorkOrderStatus()))) {
            trace.getWarnings().add("작업지시 상태·확정 검사 대상·현재 생산 양품·누적 검사수량의 정합성을 확인해 주세요.");
        }
    }
    private record MaterialLotKey(Long rawLotId, String unitCode) {}
    private record MaterialKey(Long itemId, String unitCode) {}
}
