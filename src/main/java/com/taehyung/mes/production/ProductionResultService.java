package com.taehyung.mes.production;

import com.taehyung.mes.common.actor.ActorProvider;
import com.taehyung.mes.workorder.WorkOrder;
import com.taehyung.mes.workorder.WorkOrderMapper;
import com.taehyung.mes.workorder.WorkOrderTotals;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductionResultService {
    private final ProductionResultMapper mapper;
    private final ProductionResultTransactionService transactions;
    private final ProductionResultOutcomeCodec outcomes;
    private final WorkOrderMapper orders;
    private final ActorProvider actors;
    public ProductionResultService(ProductionResultMapper mapper, ProductionResultTransactionService transactions,
                                   ProductionResultOutcomeCodec outcomes, WorkOrderMapper orders, ActorProvider actors) {
        this.mapper = mapper; this.transactions = transactions; this.outcomes = outcomes; this.orders = orders; this.actors = actors;
    }
    public ProductionResultOutcome create(ProductionResultForm form) {
        var command = ProductionResultCommand.from(form, actors.getActorId());
        try { return transactions.create(command); }
        catch (ProductionResultRequestExistsException exception) { return transactions.replay(command); }
    }
    @Transactional(readOnly=true)
    public List<ProductionResult> list(String workOrderId, String lotNo, String keyword) {
        search(lotNo, keyword); return mapper.findAll(searchId(workOrderId), lotNo, keyword);
    }
    @Transactional(readOnly=true)
    public List<FinishedGoodsLot> lots(String workOrderId, String lotNo, String keyword) {
        search(lotNo, keyword); return mapper.findLots(searchId(workOrderId), lotNo, keyword);
    }
    @Transactional(readOnly=true)
    public List<WorkOrder> openOrders() { return orders.findAll(null, null, "", "IN_PROGRESS"); }
    @Transactional(readOnly=true)
    public WorkOrder order(Long workOrderId) {
        if (workOrderId == null || workOrderId <= 0) { throw new ProductionResultRuleException("workOrderId", "작업지시를 선택해 주세요."); }
        var row = orders.findById(workOrderId);
        if (row == null) { throw new ProductionResultRuleException("workOrderId", "작업지시를 찾을 수 없습니다."); }
        return row;
    }
    @Transactional(readOnly=true)
    public FinishedGoodsLot lotForOrder(Long workOrderId) { return mapper.findLotByWorkOrder(workOrderId); }
    @Transactional(readOnly=true)
    public FinishedGoodsLot lot(Long lotId) {
        if (lotId == null || lotId <= 0) { throw new ProductionResultNotFoundException(); }
        var row = mapper.findLotById(lotId);
        if (row == null) { throw new ProductionResultNotFoundException(); }
        return row;
    }
    @Transactional(readOnly=true)
    public List<ProductionResult> resultsForLot(Long lotId) { return mapper.findByLot(lotId); }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public ProductionResult get(Long resultId) {
        if (resultId == null || resultId <= 0) { throw new ProductionResultNotFoundException(); }
        var row = mapper.findById(resultId);
        if (row == null) { throw new ProductionResultNotFoundException(); }
        row.setOutcome(outcomes.decodeInitial(row.getSnapshot(), row, mapper.findCorrections(resultId))); return row;
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public CorrectionDetails correctionDetails(Long resultId) {
        var result = get(resultId);
        var order = order(result.getWorkOrderId());
        var history = mapper.findCorrections(resultId);
        return new CorrectionDetails(result, order, orders.findTotals(result.getWorkOrderId()), history,
                "IN_PROGRESS".equals(order.getStatus()) && "EA".equals(order.getProductUnitCode())
                        && !mapper.hasInspections(result.getFinishedLotId()) && result.getRevisionNo() < 4294967295L);
    }
    public record CorrectionDetails(ProductionResult result, WorkOrder order, WorkOrderTotals totals,
                                    List<ProductionResultCorrection> history, boolean correctionAllowed) {}

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void correct(Long resultId, ProductionResultCorrectionForm form) {
        var command = ProductionResultCorrectionCommand.from(form, actors.getActorId());
        if (resultId == null || resultId <= 0) { throw new ProductionResultNotFoundException(); }
        var selected = mapper.findById(resultId);
        if (selected == null) { throw new ProductionResultNotFoundException(); }
        var order = orders.findByIdForUpdate(selected.getWorkOrderId());
        if (order == null || !"IN_PROGRESS".equals(order.getStatus())) { throw new ProductionResultRuleException(null, "IN_PROGRESS 작업지시에서만 생산실적을 정정할 수 있습니다."); }
        if (!"EA".equals(order.getProductUnitCode())) { throw new ProductionResultRuleException(null, "EA 완제품 생산실적만 정정할 수 있습니다."); }
        var locked = mapper.findByIdForUpdate(resultId);
        var current = mapper.findById(resultId);
        if (locked == null || current == null || !Objects.equals(locked.getFinishedLotId(), selected.getFinishedLotId())
                || !Objects.equals(current.getWorkOrderId(), order.getWorkOrderId())
                || !Objects.equals(current.getRevisionNo(), locked.getRevisionNo())) { throw invalid("Production result relationship changed while locking."); }
        if (current.getRevisionNo() == null || current.getRevisionNo() != command.expectedRevision()) {
            throw new ProductionResultRuleException("expectedRevision", "다른 정정이 반영되었거나 이미 처리된 버전입니다. 현재 실적과 이력을 확인하고 새 정정 화면에서 다시 입력해 주세요.");
        }
        if (mapper.hasInspections(current.getFinishedLotId())) { throw new ProductionResultRuleException(null, "품질검사 이력이 존재하는 생산실적은 정정할 수 없습니다."); }
        outcomes.decodeInitial(current.getSnapshot(), current, mapper.findCorrections(resultId));
        var totals = orders.findTotals(order.getWorkOrderId());
        if (totals == null || totals.getProducedQty() == null || totals.getGoodQty() == null || totals.getDefectQty() == null
                || totals.getProducedQty().compareTo(totals.getGoodQty().add(totals.getDefectQty())) != 0
                || totals.getProducedQty().compareTo(current.getProducedQty()) < 0
                || totals.getGoodQty().compareTo(current.getGoodQty()) < 0 || totals.getDefectQty().compareTo(current.getDefectQty()) < 0) {
            throw invalid("Current production totals are inconsistent.");
        }
        var producedAfter = totals.getProducedQty().subtract(current.getProducedQty()).add(command.producedQty());
        if (producedAfter.compareTo(order.getOrderedQty()) > 0) { throw new ProductionResultRuleException("producedQty", "정정 후 누적 생산수량이 작업지시 지시수량을 초과할 수 없습니다."); }
        var correction = new ProductionResultCorrection(); correction.setProductionResultId(resultId);
        correction.setFromRevision(current.getRevisionNo()); correction.setToRevision(current.getRevisionNo() + 1);
        correction.setBeforeProducedQty(current.getProducedQty()); correction.setAfterProducedQty(command.producedQty());
        correction.setBeforeGoodQty(current.getGoodQty()); correction.setAfterGoodQty(command.goodQty());
        correction.setBeforeDefectQty(current.getDefectQty()); correction.setAfterDefectQty(command.defectQty());
        correction.setBeforeProducedAt(current.getProducedAt()); correction.setAfterProducedAt(current.getProducedAt());
        correction.setBeforeWorkerRef(current.getWorkerRef()); correction.setAfterWorkerRef(current.getWorkerRef());
        correction.setReason(command.reason()); correction.setCreatedBy(command.actor());
        requireOne(mapper.correct(current, command)); requireOne(mapper.insertCorrection(correction));
        if (correction.getCorrectionId() == null) { throw invalid("Correction insert did not return an ID."); }
        var updated = mapper.findById(resultId);
        if (updated == null) { throw invalid("Corrected production result is missing."); }
        var storedHistory = mapper.findCorrections(resultId);
        outcomes.decodeInitial(updated.getSnapshot(), updated, storedHistory);
        var latest = storedHistory.get(storedHistory.size() - 1);
        if (!Objects.equals(latest.getCorrectionId(), correction.getCorrectionId()) || !Objects.equals(latest.getReason(), command.reason())
                || !Objects.equals(latest.getCreatedBy(), command.actor())) { throw invalid("Stored correction metadata does not match request."); }
        var after = orders.findTotals(order.getWorkOrderId());
        if (updated.getRevisionNo() != command.expectedRevision() + 1 || after.getProducedQty().compareTo(producedAfter) != 0
                || after.getGoodQty().compareTo(totals.getGoodQty().subtract(current.getGoodQty()).add(command.goodQty())) != 0
                || after.getDefectQty().compareTo(totals.getDefectQty().subtract(current.getDefectQty()).add(command.defectQty())) != 0) {
            throw invalid("Corrected production totals are inconsistent.");
        }
    }
    private void requireOne(int count) { if (count != 1) { throw invalid("Production correction write must affect exactly one row."); } }
    private DataIntegrityViolationException invalid(String message) { return new DataIntegrityViolationException(message); }
    private void search(String lotNo, String keyword) {
        if (lotNo == null || lotNo.length() > 64 || keyword == null || keyword.codePointCount(0, keyword.length()) > 200) {
            throw new ProductionResultRuleException(null, "LOT 번호는 64자, 제품 검색어는 200자 이내로 입력해 주세요.");
        }
    }
    private Long searchId(String value) {
        if ("".equals(value)) { return null; }
        if (value == null || !value.matches("[0-9]{1,19}")) { throw new ProductionResultRuleException(null, "작업지시 ID는 양의 정수로 입력해 주세요."); }
        try { long id = Long.parseLong(value); if (id > 0) { return id; } }
        catch (NumberFormatException exception) { throw new ProductionResultRuleException(null, "작업지시 ID가 조회 범위를 초과했습니다."); }
        throw new ProductionResultRuleException(null, "작업지시 ID는 양의 정수로 입력해 주세요.");
    }
}
