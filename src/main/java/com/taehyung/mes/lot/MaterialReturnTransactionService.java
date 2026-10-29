package com.taehyung.mes.lot;

import com.taehyung.mes.item.ItemUnit;
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
public class MaterialReturnTransactionService {
    private final MaterialReturnMapper mapper;
    private final WorkOrderMapper orders;
    private final MaterialReturnOutcomeCodec outcomes;
    public MaterialReturnTransactionService(MaterialReturnMapper mapper, WorkOrderMapper orders, MaterialReturnOutcomeCodec outcomes) {
        this.mapper=mapper; this.orders=orders; this.outcomes=outcomes;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW, isolation=Isolation.READ_COMMITTED)
    public MaterialReturnOutcome create(MaterialReturnCommand command) {
        var receipt=new MaterialReturnReceipt(); receipt.setScopeKey(command.scopeKey()); receipt.setRequestKey(command.requestKey()); receipt.setPayloadFingerprint(command.fingerprint()); receipt.setCreatedBy(command.actor());
        try { requireOne(mapper.claimReceipt(receipt)); }
        catch (DuplicateKeyException exception) { throw new MaterialReturnRequestExistsException(exception); }
        if (receipt.getReceiptId()==null) { throw invalid("Receipt insert did not return an ID."); }
        // 부모 ID 식별용 비잠금 조회다. 업무 값은 부모 잠금을 얻은 뒤 다시 읽는다.
        var reference=mapper.findConsumption(command.consumptionId());
        if (reference==null) { throw rule("consumptionId","반환할 원소비 이력을 찾을 수 없습니다."); }
        var order=orders.findByIdForUpdate(reference.getWorkOrderId());
        if (order==null || !"IN_PROGRESS".equals(order.getStatus())) { throw rule("consumptionId","진행 중(IN_PROGRESS)인 작업지시의 원소비에만 반환할 수 있습니다."); }
        var source=mapper.findConsumptionForUpdate(command.consumptionId());
        if (source==null || !Objects.equals(source.getConsumptionId(),command.consumptionId()) || !Objects.equals(source.getWorkOrderId(),order.getWorkOrderId())
                || !Objects.equals(source.getRawLotId(),reference.getRawLotId())) { throw invalid("Locked consumption does not match the selected parent."); }
        var lot=mapper.findLotForUpdate(source.getRawLotId());
        if (lot==null || !ItemUnit.isSupported(lot.getUnitCode())) { throw rule("consumptionId","원소비에 연결된 LOT과 보존 단위를 확인해 주세요."); }
        if ("EA".equals(lot.getUnitCode()) && command.quantity().stripTrailingZeros().scale()>0) { throw rule("returnedQty","EA 반환수량은 양의 정수여야 합니다."); }
        BigDecimal returned=mapper.sumReturned(source.getConsumptionId());
        if (returned==null || returned.signum()<0 || source.getConsumedQty()==null || source.getConsumedQty().signum()<=0 || returned.compareTo(source.getConsumedQty())>0) { throw invalid("Consumption return ledger is inconsistent."); }
        if (returned.add(command.quantity()).compareTo(source.getConsumedQty())>0) { throw rule("returnedQty","누적 반환수량은 원소비수량을 초과할 수 없습니다."); }
        BigDecimal before=lot.getAvailableQty(), after=before.add(command.quantity());
        if (before.signum()<0 || before.compareTo(lot.getRegisteredQty())>0) { throw invalid("Raw material stock is inconsistent."); }
        if (after.compareTo(MaterialReturnCommand.MAX_QTY)>0 || after.compareTo(lot.getRegisteredQty())>0) { throw rule("returnedQty","반환 후 가용수량이 LOT 등록수량 또는 저장 가능한 수량을 초과합니다."); }
        if (mapper.increaseAvailable(source.getRawLotId(),command.quantity())!=1) { throw rule("returnedQty","가용수량 복원 조건을 만족하지 못했습니다. 재고 이력을 확인해 주세요."); }
        var row=new MaterialReturn(); row.setConsumptionId(source.getConsumptionId()); row.setReceiptId(receipt.getReceiptId()); row.setReturnedQty(command.quantity()); row.setReason(command.reason()); row.setCreatedBy(command.actor());
        requireOne(mapper.insert(row)); if (row.getReturnId()==null) { throw invalid("Return insert did not return an ID."); }
        var stored=mapper.findByReceipt(receipt.getReceiptId());
        if (stored==null || !Objects.equals(stored.getReturnId(),row.getReturnId()) || !Objects.equals(stored.getReceiptId(),receipt.getReceiptId())
                || !Objects.equals(stored.getWorkOrderId(),source.getWorkOrderId()) || !Objects.equals(stored.getRawLotId(),source.getRawLotId())
                || !Objects.equals(stored.getUnitCode(),lot.getUnitCode()) || stored.getReturnedAt()==null || !matches(stored,command)) { throw invalid("Stored return does not match the command."); }
        var outcome=MaterialReturnOutcome.from(stored,before,after); String snapshot=outcomes.encode(outcome); outcomes.decode(snapshot,stored);
        requireOne(mapper.finishReceipt(receipt.getReceiptId(),snapshot));
        if (mapper.countReceiptResults(receipt.getReceiptId())!=1) { throw invalid("Receipt must reference exactly one business result."); }
        return outcome;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW, isolation=Isolation.READ_COMMITTED, readOnly=true)
    public MaterialReturnOutcome replay(MaterialReturnCommand command) {
        var receipt=mapper.findReceipt(command.scopeKey(),command.requestKey());
        if (receipt==null || receipt.getProcessedAt()==null || receipt.getSnapshot()==null || !"MATERIAL_RETURN".equals(receipt.getOperationCode()) || !Objects.equals(receipt.getCreatedBy(),command.actor())) { throw invalid("No complete return receipt is available after UNIQUE conflict."); }
        if (receipt.getPayloadFingerprint()==null || !MessageDigest.isEqual(receipt.getPayloadFingerprint(),command.fingerprint())) { throw rule("requestKey","이미 처리한 요청 키에 다른 원소비·수량·사유를 사용할 수 없습니다. 새 반환은 원소비 상세에서 시작해 주세요."); }
        var row=mapper.findByReceipt(receipt.getReceiptId());
        if (row==null || !Objects.equals(row.getReceiptId(),receipt.getReceiptId()) || !matches(row,command) || mapper.countReceiptResults(receipt.getReceiptId())!=1) { throw invalid("Return receipt does not identify exactly one matching result."); }
        return outcomes.decode(receipt.getSnapshot(),row);
    }
    private boolean matches(MaterialReturn row, MaterialReturnCommand command) {
        return Objects.equals(row.getConsumptionId(),command.consumptionId()) && row.getReturnedQty()!=null && row.getReturnedQty().compareTo(command.quantity())==0
                && Objects.equals(row.getReason(),command.reason()) && Objects.equals(row.getCreatedBy(),command.actor());
    }
    private void requireOne(int count) { if (count!=1) { throw invalid("Return write must affect exactly one row."); } }
    private DataIntegrityViolationException invalid(String message) { return new DataIntegrityViolationException(message); }
    private MaterialReturnRuleException rule(String field,String message) { return new MaterialReturnRuleException(field,message); }
}
