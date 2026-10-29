package com.taehyung.mes.production;

import com.taehyung.mes.workorder.WorkOrderMapper;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductionResultTransactionService {
    private final ProductionResultMapper mapper;
    private final WorkOrderMapper orders;
    private final ProductionResultOutcomeCodec outcomes;
    public ProductionResultTransactionService(ProductionResultMapper mapper, WorkOrderMapper orders, ProductionResultOutcomeCodec outcomes) {
        this.mapper = mapper; this.orders = orders; this.outcomes = outcomes;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW, isolation=Isolation.READ_COMMITTED)
    public ProductionResultOutcome create(ProductionResultCommand command) {
        var receipt = new ProductionResultReceipt(); receipt.setScopeKey(command.scopeKey()); receipt.setRequestKey(command.requestKey());
        receipt.setPayloadFingerprint(command.fingerprint()); receipt.setCreatedBy(command.actor());
        try { requireOne(mapper.claimReceipt(receipt)); }
        catch (DuplicateKeyException exception) { throw new ProductionResultRequestExistsException(exception); }
        if (receipt.getReceiptId() == null) { throw invalid("Receipt insert did not return an ID."); }

        var order = orders.findByIdForUpdate(command.workOrderId());
        if (order == null || !"IN_PROGRESS".equals(order.getStatus())) { throw rule("workOrderId", "진행 중인 작업지시에만 생산실적을 등록할 수 있습니다."); }
        if (!"EA".equals(order.getProductUnitCode())) { throw rule("workOrderId", "완제품 관리단위가 EA인 작업지시만 등록할 수 있습니다."); }
        var totals = orders.findTotals(command.workOrderId());
        if (totals == null || totals.getResultCount() == null || totals.getProducedQty() == null
                || totals.getGoodQty() == null || totals.getDefectQty() == null) { throw invalid("Production totals are unavailable."); }
        if (totals.getProducedQty().add(command.producedQty()).compareTo(order.getOrderedQty()) > 0) {
            throw rule("producedQty", "누적 생산수량이 작업지시 지시수량을 초과할 수 없습니다.");
        }
        FinishedGoodsLot lot = mapper.findLotByWorkOrder(command.workOrderId());
        if (lot == null) {
            if (command.lotNo() == null) { throw rule("lotNo", "첫 생산실적에는 완제품 LOT 번호를 입력해 주세요."); }
            if (totals.getResultCount() != 0) { throw invalid("Production results exist without a finished lot."); }
            if (mapper.existsLotNo(command.lotNo())) { throw rule("lotNo", "이미 사용 중인 완제품 LOT 번호입니다."); }
            lot = new FinishedGoodsLot(); lot.setWorkOrderId(command.workOrderId()); lot.setLotNo(command.lotNo()); lot.setCreatedBy(command.actor());
            try { requireOne(mapper.insertLot(lot)); }
            catch (DuplicateKeyException exception) { throw rule("lotNo", "이미 사용 중인 완제품 LOT 번호입니다."); }
            if (lot.getFinishedLotId() == null) { throw invalid("Finished lot insert did not return an ID."); }
        } else {
            if (command.lotNo() != null) { throw rule("lotNo", "이 작업지시에는 이미 완제품 LOT이 있습니다. 새 실적에서는 LOT 번호를 입력하지 마세요."); }
            if (totals.getResultCount() == 0) { throw invalid("Finished lot exists without a production result."); }
        }
        var row = new ProductionResult(); row.setFinishedLotId(lot.getFinishedLotId()); row.setReceiptId(receipt.getReceiptId());
        row.setProducedQty(command.producedQty()); row.setGoodQty(command.goodQty()); row.setDefectQty(command.defectQty());
        row.setWorkerRef(command.workerRef()); row.setCreatedBy(command.actor());
        requireOne(mapper.insert(row));
        if (row.getProductionResultId() == null) { throw invalid("Production result insert did not return an ID."); }
        var stored = mapper.findByReceipt(receipt.getReceiptId());
        if (!matches(stored, command) || !Objects.equals(stored.getProductionResultId(), row.getProductionResultId())
                || !Objects.equals(stored.getFinishedLotId(), lot.getFinishedLotId()) || stored.getProducedAt() == null
                || !Objects.equals(stored.getLotNo(), lot.getLotNo()) || stored.getRevisionNo() == null || stored.getRevisionNo() != 1) {
            throw invalid("Stored production result does not match the request.");
        }
        var outcome = ProductionResultOutcome.from(stored);
        String snapshot = outcomes.encode(outcome); outcomes.decode(snapshot, stored);
        requireOne(mapper.finishReceipt(receipt.getReceiptId(), snapshot));
        if (mapper.countReceiptResults(receipt.getReceiptId()) != 1) { throw invalid("Receipt must identify exactly one production result."); }
        return outcome;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW, isolation=Isolation.REPEATABLE_READ, readOnly=true)
    public ProductionResultOutcome replay(ProductionResultCommand command) {
        var receipt = mapper.findReceipt(command.scopeKey(), command.requestKey());
        if (receipt == null || receipt.getProcessedAt() == null || receipt.getSnapshot() == null
                || !"PRODUCTION_RESULT".equals(receipt.getOperationCode()) || !Objects.equals(receipt.getCreatedBy(), command.actor())) {
            throw invalid("No complete production receipt is available after UNIQUE conflict.");
        }
        if (receipt.getPayloadFingerprint() == null || !MessageDigest.isEqual(receipt.getPayloadFingerprint(), command.fingerprint())) {
            throw rule("requestKey", "이미 처리한 요청 키에 다른 작업지시·LOT·수량·작업자를 사용할 수 없습니다. 새 등록에는 새 요청 키를 사용해 주세요.");
        }
        var row = mapper.findByReceipt(receipt.getReceiptId());
        if (row == null) { throw invalid("Production receipt has no result."); }
        var initial = outcomes.decodeInitial(receipt.getSnapshot(), row, mapper.findCorrections(row.getProductionResultId()));
        if (!Objects.equals(initial.workOrderId(), command.workOrderId()) || !Objects.equals(row.getCreatedBy(), command.actor())
                || new BigDecimal(initial.producedQty()).compareTo(command.producedQty()) != 0
                || new BigDecimal(initial.goodQty()).compareTo(command.goodQty()) != 0
                || new BigDecimal(initial.defectQty()).compareTo(command.defectQty()) != 0
                || !Objects.equals(initial.workerRef(), command.workerRef())
                || (command.lotNo() != null && !Objects.equals(initial.lotNo(), command.lotNo()))
                || mapper.countReceiptResults(receipt.getReceiptId()) != 1) { throw invalid("Production receipt does not identify one matching result."); }
        return initial;
    }
    private boolean matches(ProductionResult row, ProductionResultCommand command) {
        return row != null && Objects.equals(row.getWorkOrderId(), command.workOrderId())
                && row.getProducedQty() != null && row.getProducedQty().compareTo(command.producedQty()) == 0
                && row.getGoodQty() != null && row.getGoodQty().compareTo(command.goodQty()) == 0
                && row.getDefectQty() != null && row.getDefectQty().compareTo(command.defectQty()) == 0
                && Objects.equals(row.getWorkerRef(), command.workerRef()) && Objects.equals(row.getCreatedBy(), command.actor());
    }
    private void requireOne(int count) { if (count != 1) { throw invalid("Production write must affect exactly one row."); } }
    private DataIntegrityViolationException invalid(String message) { return new DataIntegrityViolationException(message); }
    private ProductionResultRuleException rule(String field, String message) { return new ProductionResultRuleException(field, message); }
}
