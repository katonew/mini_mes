package com.taehyung.mes.quality;

import com.taehyung.mes.workorder.WorkOrderMapper;
import java.security.MessageDigest;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QualityInspectionTransactionService {
    private final QualityInspectionMapper mapper;
    private final WorkOrderMapper orders;
    private final QualityInspectionOutcomeCodec outcomes;
    public QualityInspectionTransactionService(QualityInspectionMapper mapper, WorkOrderMapper orders, QualityInspectionOutcomeCodec outcomes) {
        this.mapper = mapper; this.orders = orders; this.outcomes = outcomes;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW, isolation=Isolation.READ_COMMITTED)
    public QualityInspectionOutcome create(QualityInspectionCommand command) {
        var receipt = new QualityInspectionReceipt(); receipt.setScopeKey(command.scopeKey()); receipt.setRequestKey(command.requestKey());
        receipt.setPayloadFingerprint(command.fingerprint()); receipt.setCreatedBy(command.actor());
        try { requireOne(mapper.claimReceipt(receipt)); }
        catch (DuplicateKeyException exception) { throw new QualityInspectionRequestExistsException(exception); }
        if (receipt.getReceiptId() == null) { throw invalid("Receipt insert did not return an ID."); }
        var selected = mapper.findLot(command.finishedLotId());
        if (selected == null) { throw rule("finishedLotId", "완제품 LOT을 찾을 수 없습니다."); }
        var order = orders.findByIdForUpdate(selected.getWorkOrderId());
        if (order == null || !"COMPLETED".equals(order.getStatus())) { throw rule("finishedLotId", "COMPLETED 작업지시의 완제품 LOT만 검사할 수 있습니다."); }
        if (!"EA".equals(order.getProductUnitCode())) { throw rule("finishedLotId", "완제품 관리단위가 EA인 작업지시만 검사할 수 있습니다."); }
        // 부모 잠금 후 READ_COMMITTED로 최신 확정 대상·검사 합계·회차를 다시 읽는다.
        var lot = mapper.findLot(command.finishedLotId());
        if (lot == null || !Objects.equals(lot.getWorkOrderId(), order.getWorkOrderId())) { throw invalid("Inspection lot parent changed."); }
        QualityInspectionService.validateTotals(lot);
        if (lot.getUninspectedQty() == null) { throw invalid("Completed work order has no inspection target."); }
        if (command.inspectedQty().compareTo(lot.getUninspectedQty()) > 0) { throw rule("inspectedQty", "검사수량은 확정 검사 대상의 미검사수량을 초과할 수 없습니다."); }
        if (lot.getLastRoundNo() >= 4294967295L) { throw rule(null, "검사 회차가 DB 저장 범위를 초과했습니다."); }
        var row = new QualityInspection(); row.setFinishedLotId(command.finishedLotId()); row.setReceiptId(receipt.getReceiptId());
        row.setRoundNo(lot.getLastRoundNo() + 1); row.setInspectedQty(command.inspectedQty()); row.setAcceptedQty(command.acceptedQty());
        row.setRejectedQty(command.rejectedQty()); row.setInspectorRef(command.inspectorRef());
        row.setJudgementReason(command.judgementReason()); row.setCreatedBy(command.actor()); requireOne(mapper.insert(row));
        var stored = mapper.findByReceipt(receipt.getReceiptId());
        if (row.getInspectionId() == null || !matches(stored, command) || !Objects.equals(stored.getInspectionId(), row.getInspectionId())
                || !Objects.equals(stored.getRoundNo(), row.getRoundNo()) || stored.getInspectedAt() == null) { throw invalid("Stored inspection does not match request."); }
        var outcome = QualityInspectionOutcome.from(stored);
        String snapshot = outcomes.encode(outcome); outcomes.decode(snapshot, stored);
        requireOne(mapper.finishReceipt(receipt.getReceiptId(), snapshot));
        if (mapper.countReceiptResults(receipt.getReceiptId()) != 1) { throw invalid("Receipt must identify exactly one inspection."); }
        return outcome;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW, isolation=Isolation.READ_COMMITTED, readOnly=true)
    public QualityInspectionOutcome replay(QualityInspectionCommand command) {
        var receipt = mapper.findReceipt(command.scopeKey(), command.requestKey());
        if (receipt == null || receipt.getProcessedAt() == null || receipt.getSnapshot() == null
                || !"QUALITY_INSPECTION".equals(receipt.getOperationCode()) || !Objects.equals(receipt.getCreatedBy(), command.actor())) {
            throw invalid("No complete inspection receipt is available after UNIQUE conflict.");
        }
        if (receipt.getPayloadFingerprint() == null || !MessageDigest.isEqual(receipt.getPayloadFingerprint(), command.fingerprint())) {
            throw rule("requestKey", "이미 처리한 요청 키에 다른 LOT·수량·검사자·사유를 사용할 수 없습니다. 새 검사에는 새 요청 키를 사용해 주세요.");
        }
        var row = mapper.findByReceipt(receipt.getReceiptId());
        if (!matches(row, command) || mapper.countReceiptResults(receipt.getReceiptId()) != 1) { throw invalid("Inspection receipt does not identify one matching result."); }
        return outcomes.decode(receipt.getSnapshot(), row);
    }
    private boolean matches(QualityInspection row, QualityInspectionCommand command) {
        return row != null && Objects.equals(row.getFinishedLotId(), command.finishedLotId())
                && row.getInspectedQty() != null && row.getInspectedQty().compareTo(command.inspectedQty()) == 0
                && row.getAcceptedQty() != null && row.getAcceptedQty().compareTo(command.acceptedQty()) == 0
                && row.getRejectedQty() != null && row.getRejectedQty().compareTo(command.rejectedQty()) == 0
                && Objects.equals(row.getInspectorRef(), command.inspectorRef()) && Objects.equals(row.getJudgementReason(), command.judgementReason())
                && Objects.equals(row.getCreatedBy(), command.actor());
    }
    private void requireOne(int count) { if (count != 1) { throw invalid("Inspection write must affect exactly one row."); } }
    private DataIntegrityViolationException invalid(String message) { return new DataIntegrityViolationException(message); }
    private QualityInspectionRuleException rule(String field, String message) { return new QualityInspectionRuleException(field, message); }
}
