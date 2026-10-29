package com.taehyung.mes.quality;

import com.taehyung.mes.common.actor.ActorProvider;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QualityInspectionService {
    private final QualityInspectionMapper mapper;
    private final QualityInspectionTransactionService transactions;
    private final QualityInspectionOutcomeCodec outcomes;
    private final ActorProvider actors;
    public QualityInspectionService(QualityInspectionMapper mapper, QualityInspectionTransactionService transactions,
                                    QualityInspectionOutcomeCodec outcomes, ActorProvider actors) {
        this.mapper = mapper; this.transactions = transactions; this.outcomes = outcomes; this.actors = actors;
    }
    public QualityInspectionOutcome create(QualityInspectionForm form) {
        var command = QualityInspectionCommand.from(form, actors.getActorId());
        try { return transactions.create(command); }
        catch (QualityInspectionRequestExistsException exception) { return transactions.replay(command); }
    }
    @Transactional(readOnly=true)
    public List<QualityInspection> list(String workOrderId, String lotId, String lotNo, String keyword) {
        if (lotNo == null || lotNo.length() > 64 || keyword == null || keyword.codePointCount(0, keyword.length()) > 200) {
            throw new QualityInspectionRuleException(null, "LOT 번호는 64자, 제품 검색어는 200자 이내로 입력해 주세요.");
        }
        return mapper.findAll(searchId(workOrderId), searchId(lotId), lotNo, keyword);
    }
    @Transactional(readOnly=true)
    public QualityInspection get(Long inspectionId) {
        if (inspectionId == null || inspectionId <= 0) { throw new QualityInspectionNotFoundException(); }
        var row = mapper.findById(inspectionId);
        if (row == null) { throw new QualityInspectionNotFoundException(); }
        outcomes.decode(row.getSnapshot(), row); return row;
    }
    @Transactional(readOnly=true)
    public List<QualityInspectionLot> eligibleLots() {
        var rows = mapper.findEligibleLots(); rows.forEach(QualityInspectionService::validateTotals); return rows;
    }
    @Transactional(readOnly=true)
    public QualityInspectionLot lot(Long lotId) {
        if (lotId == null || lotId <= 0) { throw new QualityInspectionNotFoundException(); }
        var row = mapper.findLot(lotId);
        if (row == null) { throw new QualityInspectionNotFoundException(); }
        validateTotals(row); return row;
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public QualityInspectionHistory history(Long lotId) {
        var lot = lot(lotId);
        return new QualityInspectionHistory(lot, mapper.findAll(null, lotId, "", ""));
    }
    public record QualityInspectionHistory(QualityInspectionLot lot, List<QualityInspection> inspections) {}
    static void validateTotals(QualityInspectionLot lot) {
        if (lot.getInspectedQty() == null || lot.getAcceptedQty() == null || lot.getRejectedQty() == null || lot.getLastRoundNo() == null
                || lot.getInspectedQty().signum() < 0 || lot.getAcceptedQty().signum() < 0 || lot.getRejectedQty().signum() < 0
                || lot.getInspectedQty().compareTo(lot.getAcceptedQty().add(lot.getRejectedQty())) != 0
                || ("COMPLETED".equals(lot.getWorkOrderStatus()) && lot.getInspectionTargetQty() == null)
                || (lot.getInspectionTargetQty() != null && (lot.getInspectionTargetQty().signum() < 0
                    || lot.getInspectionTargetQty().stripTrailingZeros().scale() > 0 || lot.getUninspectedQty().signum() < 0))) {
            throw new DataIntegrityViolationException("Inspection target and totals are inconsistent.");
        }
    }
    private Long searchId(String value) {
        if ("".equals(value)) { return null; }
        if (value == null || !value.matches("[0-9]{1,19}")) { throw new QualityInspectionRuleException(null, "조회 ID는 양의 정수로 입력해 주세요."); }
        try { long id = Long.parseLong(value); if (id > 0) { return id; } }
        catch (NumberFormatException exception) { throw new QualityInspectionRuleException(null, "ID가 조회 범위를 초과했습니다."); }
        throw new QualityInspectionRuleException(null, "조회 ID는 양의 정수로 입력해 주세요.");
    }
}
