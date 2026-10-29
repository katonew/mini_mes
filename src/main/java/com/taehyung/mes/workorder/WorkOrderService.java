package com.taehyung.mes.workorder;

import com.taehyung.mes.bom.Bom;
import com.taehyung.mes.bom.BomComponent;
import com.taehyung.mes.bom.BomMapper;
import com.taehyung.mes.bom.BomVersion;
import com.taehyung.mes.common.actor.ActorProvider;
import com.taehyung.mes.item.Item;
import com.taehyung.mes.item.ItemMapper;
import com.taehyung.mes.item.ItemUnit;
import com.taehyung.mes.process.ProcessMapper;
import com.taehyung.mes.routing.Routing;
import com.taehyung.mes.routing.RoutingMapper;
import com.taehyung.mes.routing.RoutingRevision;
import com.taehyung.mes.routing.RoutingStep;
import com.taehyung.mes.production.ProductionPlan;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class WorkOrderService {
    private static final BigDecimal MAX_QTY = new BigDecimal("999999999999");
    private final WorkOrderMapper mapper;
    private final BomMapper boms;
    private final RoutingMapper routings;
    private final ItemMapper items;
    private final ProcessMapper processes;
    private final ActorProvider actors;

    public WorkOrderService(WorkOrderMapper mapper, BomMapper boms, RoutingMapper routings, ItemMapper items, ProcessMapper processes, ActorProvider actors) {
        this.mapper = mapper; this.boms = boms; this.routings = routings; this.items = items; this.processes = processes; this.actors = actors;
    }

    @Transactional(readOnly = true)
    public List<WorkOrder> list(String id, String planId, String keyword, String status) {
        if (keyword == null || keyword.codePointCount(0, keyword.length()) > 200 || status == null || (!status.isEmpty() && !WorkOrderStatus.supports(status))) {
            throw rule(null, "검색어는 200자 이내로 입력하고 등록된 작업 상태를 선택해 주세요.");
        }
        return mapper.findAll(searchId(id), searchId(planId), keyword, status);
    }

    @Transactional(readOnly = true)
    public List<ProductionPlan> plans() { return mapper.findPlans(); }

    @Transactional(readOnly = true)
    public WorkOrderOptions options(Long planId) {
        requireId(planId);
        ProductionPlan plan = mapper.findPlan(planId);
        if (plan == null) { throw rule("productionPlanId", "존재하는 생산계획을 선택해 주세요."); }
        Item product = items.findById(plan.getProductItemId());
        WorkOrderOptions result = new WorkOrderOptions(); result.setPlan(plan); result.setAllocation(mapper.findAllocation(planId));
        result.setProductActive(product != null && "FINISHED".equals(product.getItemKind()) && Integer.valueOf(1).equals(product.getIsActive()));
        List<WorkOrderBomOption> choices = mapper.findBomOptions(plan.getProductItemId());
        for (WorkOrderBomOption choice : choices) {
            if (choice.getComponentCount() == 0) { choice.setUnavailableReason("구성 원자재 없음"); }
            else if (product == null || !Objects.equals(choice.getProductUnitCode(), product.getUnitCode())) { choice.setUnavailableReason("제품 기준단위 불일치"); }
            else if (choice.getBaseQty().signum() <= 0) { choice.setUnavailableReason("유효하지 않은 기준 생산수량"); }
        }
        result.setBoms(choices); result.setRoutings(mapper.findRoutingOptions(plan.getProductItemId()));
        return result;
    }

    @Transactional(readOnly = true)
    public WorkOrderDetails get(Long id) {
        requireId(id); WorkOrder order = mapper.findById(id);
        if (order == null) { throw new WorkOrderNotFoundException(); }
        WorkOrderDetails details = new WorkOrderDetails(); details.setOrder(order); details.setTotals(mapper.findTotals(id));
        details.setSteps(routings.findSteps(order.getRoutingRevisionId()));
        details.setFinishedLot(mapper.findFinishedLot(id)); details.setResults(mapper.findProductionResults(id));
        details.setCompletionReady(completionReady(order, details.getTotals()));
        return details;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long create(WorkOrderForm form) {
        validateForm(form); String actor = actor();
        ProductionPlan plan = mapper.findPlanForUpdate(form.getProductionPlanId());
        if (plan == null) { throw rule("productionPlanId", "존재하는 생산계획을 선택해 주세요."); }
        if (!Objects.equals(plan.getProductItemId(), form.getProductItemId())) { throw rule("productItemId", "작업 대상 완제품은 생산계획의 제품과 같아야 합니다."); }
        BomVersion locatedBom = boms.findVersion(form.getBomVersionId());
        RoutingRevision locatedRouting = routings.findRevision(form.getRoutingRevisionId());
        if (locatedBom == null) { throw rule("bomVersionId", "BOM 버전이 존재하지 않습니다."); }
        if (locatedRouting == null) { throw rule("routingRevisionId", "Routing 개정이 존재하지 않습니다."); }
        // 부모 식별 조회 이후 모든 검증은 잠긴 최신 행을 기준으로 다시 한다.
        Bom bom = boms.findBomForUpdate(locatedBom.getBomId());
        Routing routing = routings.findRoutingForUpdate(locatedRouting.getRoutingId());
        BomVersion version = boms.findVersionForUpdate(form.getBomVersionId());
        RoutingRevision revision = mapper.findRevisionForUpdate(form.getRoutingRevisionId());
        if (bom == null || version == null || !Objects.equals(version.getBomId(), bom.getBomId()) || !Objects.equals(bom.getProductItemId(), plan.getProductItemId())) {
            throw rule("bomVersionId", "생산계획 제품에 속한 BOM 버전을 선택해 주세요.");
        }
        if (routing == null || revision == null || !Objects.equals(revision.getRoutingId(), routing.getRoutingId()) || !Objects.equals(routing.getProductItemId(), plan.getProductItemId())) {
            throw rule("routingRevisionId", "생산계획 제품에 속한 Routing 개정을 선택해 주세요.");
        }
        List<BomComponent> components = boms.findComponents(version.getBomVersionId());
        List<RoutingStep> steps = routings.findSteps(revision.getRoutingRevisionId());
        if (components.isEmpty()) { throw rule("bomVersionId", "구성 원자재가 있는 BOM 버전을 선택해 주세요."); }
        if (steps.isEmpty()) { throw rule("routingRevisionId", "공정 구성이 있는 Routing 개정을 선택해 주세요."); }
        Map<Long, Item> lockedItems = lockItems(plan.getProductItemId(), components);
        Item product = lockedItems.get(plan.getProductItemId());
        if (product == null || !"FINISHED".equals(product.getItemKind()) || !Integer.valueOf(1).equals(product.getIsActive()) || !ItemUnit.isSupported(product.getUnitCode())) {
            throw rule("productItemId", "사용 중인 FINISHED 완제품만 신규 작업지시에 사용할 수 있습니다.");
        }
        if (!Objects.equals(version.getProductUnitCode(), product.getUnitCode()) || version.getBaseQty().signum() <= 0 || version.getBaseQty().stripTrailingZeros().scale() > 0) {
            throw rule("bomVersionId", "BOM의 제품 단위와 기준 생산수량을 확인해 주세요.");
        }
        validateComponents(components, lockedItems);
        validateProcesses(steps);
        if (version.getFirstUsedAt() == null) { requireOne(mapper.markBomUsed(version.getBomVersionId())); }
        if (revision.getFirstUsedAt() == null) { requireOne(mapper.markRoutingUsed(revision.getRoutingRevisionId())); }
        WorkOrder order = new WorkOrder(); order.setProductionPlanId(plan.getProductionPlanId()); order.setProductItemId(product.getItemId());
        order.setBomVersionId(version.getBomVersionId()); order.setRoutingRevisionId(revision.getRoutingRevisionId());
        order.setOrderedQty(form.getOrderedQty()); order.setProductUnitCode(product.getUnitCode()); order.setCreatedBy(actor);
        requireOne(mapper.insert(order));
        if (order.getWorkOrderId() == null) { throw new DataIntegrityViolationException("Work order insert did not return an ID."); }
        return order.getWorkOrderId();
    }

    @Transactional
    public void transition(Long id, WorkOrderTransitionForm form) {
        requireId(id);
        if (form == null || (!"START".equals(form.getAction()) && !"CANCEL".equals(form.getAction()))) {
            throw rule(null, "현재 지원하는 작업은 시작과 시작 전 취소입니다. 완료 처리는 생산실적 기능과 연계합니다.");
        }
        String actor = actor(); WorkOrder order = mapper.findByIdForUpdate(id);
        if (order == null) { throw new WorkOrderNotFoundException(); }
        if (!"READY".equals(order.getStatus())) { throw rule(null, "대기 상태에서만 시작하거나 취소할 수 있습니다. 현재 상태를 다시 확인해 주세요."); }
        if (mapper.hasExecutionHistory(id)) { throw rule(null, "투입 또는 LOT·생산 이력이 있는 작업지시는 시작 전 상태로 처리할 수 없습니다."); }
        requireOne("START".equals(form.getAction()) ? mapper.start(id, actor) : mapper.cancel(id, actor));
    }

    /** 후속 생산실적 구현에서 사용할 완료 조건 검증. 상태를 갱신하는 기능이 아니다. */
    @Transactional
    public void validateCompletion(Long id) {
        requireId(id); WorkOrder order = mapper.findByIdForUpdate(id);
        if (order == null) { throw new WorkOrderNotFoundException(); }
        if (!completionReady(order, mapper.findTotals(id))) { throw rule(null, "진행 중이며 실제 생산실적이 존재하고 누적 생산수량이 지시수량과 같아야 완료할 수 있습니다."); }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void complete(Long id) {
        requireId(id); String completedBy = actor();
        WorkOrder order = mapper.findByIdForUpdate(id);
        if (order == null) { throw new WorkOrderNotFoundException(); }
        WorkOrderTotals totals = mapper.findTotals(id);
        if (!completionReady(order, totals)) {
            throw rule(null, "진행 중이며 생산실적이 있고 누적 생산수량이 지시수량과 같을 때만 완료할 수 있습니다.");
        }
        if (totals.getGoodQty() == null || totals.getDefectQty() == null
                || totals.getGoodQty().signum() < 0 || totals.getDefectQty().signum() < 0
                || totals.getGoodQty().add(totals.getDefectQty()).compareTo(totals.getProducedQty()) != 0
                || totals.getGoodQty().compareTo(order.getOrderedQty()) > 0) {
            throw new DataIntegrityViolationException("Production totals are inconsistent at completion.");
        }
        requireOne(mapper.complete(id, totals.getGoodQty(), completedBy));
    }

    private boolean completionReady(WorkOrder order, WorkOrderTotals totals) {
        return totals != null && totals.getResultCount() != null && totals.getProducedQty() != null
                && "IN_PROGRESS".equals(order.getStatus()) && totals.getResultCount() > 0 && totals.getProducedQty().signum() > 0
                && totals.getProducedQty().compareTo(order.getOrderedQty()) == 0;
    }
    private Map<Long, Item> lockItems(Long productId, List<BomComponent> components) {
        var ids = new TreeSet<Long>(); ids.add(productId); components.forEach(row -> ids.add(row.getMaterialItemId()));
        Map<Long, Item> result = new HashMap<>(); for (Long id : ids) { result.put(id, items.findByIdForUpdate(id)); } return result;
    }
    private void validateComponents(List<BomComponent> components, Map<Long, Item> locked) {
        for (BomComponent row : components) {
            Item raw = locked.get(row.getMaterialItemId());
            if (raw == null || !"RAW".equals(raw.getItemKind()) || !ItemUnit.isSupported(raw.getUnitCode()) || !Objects.equals(raw.getUnitCode(), row.getMaterialUnitCode())
                    || row.getRequiredQty().signum() <= 0 || ("EA".equals(raw.getUnitCode()) && row.getRequiredQty().stripTrailingZeros().scale() > 0)) {
                throw rule("bomVersionId", "BOM 구성의 품목·보존 단위·소요량 정합성을 확인해 주세요.");
            }
        }
    }
    private void validateProcesses(List<RoutingStep> steps) {
        var ids = new TreeSet<Long>(); steps.forEach(row -> ids.add(row.getProcessId()));
        for (Long id : ids) {
            var process = processes.findByIdForUpdate(id);
            if (process == null || !Integer.valueOf(1).equals(process.getIsActive())) { throw rule("routingRevisionId", "Routing에 미사용 공정이 포함되어 신규 작업지시를 등록할 수 없습니다."); }
        }
        for (RoutingStep row : steps) {
            if (row.getProcessNameSnapshot() == null || row.getProcessNameSnapshot().isBlank()) { throw rule("routingRevisionId", "공정명 스냅샷이 유효한 Routing 개정을 선택해 주세요."); }
        }
    }
    private void validateForm(WorkOrderForm form) {
        if (form == null) { throw rule(null, "작업지시 입력값이 필요합니다."); }
        if (form.getProductionPlanId() == null || form.getProductionPlanId() <= 0) { throw rule("productionPlanId", "생산계획을 선택해 주세요."); }
        if (form.getProductItemId() == null || form.getProductItemId() <= 0) { throw rule("productItemId", "생산계획의 완제품을 확인해 주세요."); }
        if (form.getBomVersionId() == null || form.getBomVersionId() <= 0) { throw rule("bomVersionId", "BOM 버전을 선택해 주세요."); }
        if (form.getRoutingRevisionId() == null || form.getRoutingRevisionId() <= 0) { throw rule("routingRevisionId", "Routing 개정을 선택해 주세요."); }
        BigDecimal qty = form.getOrderedQty();
        if (qty == null || qty.signum() <= 0 || qty.compareTo(MAX_QTY) > 0 || qty.stripTrailingZeros().scale() > 0) { throw rule("orderedQty", "지시수량은 1~999999999999의 정수로 입력해 주세요."); }
    }
    private Long searchId(String value) {
        if (value != null && value.isEmpty()) { return null; }
        if (value == null || !value.matches("[0-9]{1,19}")) { throw rule(null, "검색 ID는 양의 정수로 입력해 주세요."); }
        try { long id = Long.parseLong(value); if (id > 0) { return id; } }
        catch (NumberFormatException exception) { throw rule(null, "검색 ID가 조회 가능한 범위를 초과했습니다."); }
        throw rule(null, "검색 ID는 양의 정수로 입력해 주세요.");
    }
    private String actor() { String id = actors.getActorId(); if (id == null || id.isBlank() || id.codePointCount(0, id.length()) > 64) { throw rule(null, "MES_ACTOR_ID는 공백이 아닌 64자 이하로 설정해야 합니다."); } return id; }
    private void requireId(Long id) { if (id == null || id <= 0) { throw new WorkOrderNotFoundException(); } }
    private void requireOne(int count) { if (count != 1) { throw new DataIntegrityViolationException("Work order write must affect exactly one row."); } }
    private WorkOrderRuleException rule(String field, String message) { return new WorkOrderRuleException(field, message); }
}
