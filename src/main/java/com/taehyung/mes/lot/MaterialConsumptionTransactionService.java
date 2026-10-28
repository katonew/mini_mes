package com.taehyung.mes.lot;

import com.taehyung.mes.bom.BomMapper;
import com.taehyung.mes.bom.BomComponent;
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

/** 별도 Bean의 프록시를 거쳐야 Receipt 충돌 시 롤백 후 새 읽기 트랜잭션을 사용할 수 있다. */
@Service
public class MaterialConsumptionTransactionService {
    private final MaterialConsumptionMapper mapper;
    private final WorkOrderMapper orders;
    private final BomMapper boms;
    private final MaterialConsumptionOutcomeCodec outcomes;
    public MaterialConsumptionTransactionService(MaterialConsumptionMapper mapper, WorkOrderMapper orders, BomMapper boms, MaterialConsumptionOutcomeCodec outcomes) {
        this.mapper = mapper; this.orders = orders; this.boms = boms; this.outcomes = outcomes;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW, isolation=Isolation.READ_COMMITTED)
    public MaterialConsumptionOutcome create(MaterialConsumptionCommand command) {
        var receipt = new MaterialConsumptionReceipt(); receipt.setScopeKey(command.scopeKey()); receipt.setRequestKey(command.requestKey());
        receipt.setPayloadFingerprint(command.fingerprint()); receipt.setCreatedBy(command.actor());
        try { requireOne(mapper.claimReceipt(receipt)); }
        catch (DuplicateKeyException exception) { throw new MaterialConsumptionRequestExistsException(exception); }
        if (receipt.getReceiptId() == null) { throw invalid("Receipt insert did not return an ID."); }
        var order = orders.findByIdForUpdate(command.workOrderId());
        if (order == null || !"IN_PROGRESS".equals(order.getStatus())) { throw rule("workOrderId", "진행 중(IN_PROGRESS)인 작업지시에만 새 원자재 투입을 등록할 수 있습니다."); }
        // 이미 사용된 적용 버전은 기존 BOM 편집 규약에서 불변이다. WorkOrder 뒤에 기준정보 편집 잠금을 추가하지 않는다.
        var version = boms.findVersion(order.getBomVersionId());
        if (version == null || !Objects.equals(version.getProductItemId(), order.getProductItemId())
                || !Objects.equals(version.getProductUnitCode(), order.getProductUnitCode()) || version.getBaseQty().signum() <= 0) {
            throw rule("bomComponentId", "작업지시에 보존된 BOM 버전의 제품·단위·기준수량 정합성을 확인해 주세요.");
        }
        BomComponent component = boms.findComponents(order.getBomVersionId()).stream()
                .filter(row -> Objects.equals(row.getBomComponentId(), command.bomComponentId())).findFirst().orElse(null);
        if (component == null || !Objects.equals(component.getBomVersionId(), order.getBomVersionId())) {
            throw rule("bomComponentId", "해당 작업지시에 확정된 BOM 버전의 구성 원자재를 선택해 주세요.");
        }
        var lot = mapper.findLotForUpdate(command.rawLotId());
        if (lot == null || !Objects.equals(lot.getItemId(), component.getMaterialItemId())
                || !Objects.equals(lot.getUnitCode(), component.getMaterialUnitCode()) || !ItemUnit.isSupported(lot.getUnitCode())) {
            throw rule("rawLotId", "원자재 LOT의 품목·보존 단위가 선택한 BOM 구성과 일치해야 합니다.");
        }
        if ("EA".equals(lot.getUnitCode()) && command.quantity().stripTrailingZeros().scale() > 0) { throw rule("consumedQty", "EA 원자재 투입수량은 양의 정수여야 합니다."); }
        BigDecimal before = lot.getAvailableQty(), after = before.subtract(command.quantity());
        if (after.signum() < 0) { throw rule("consumedQty", "원자재 LOT의 현재 가용수량보다 많이 투입할 수 없습니다."); }
        if (mapper.decreaseAvailable(command.rawLotId(), command.quantity()) != 1) { throw rule("consumedQty", "가용수량 차감 조건을 만족하지 못했습니다. 현재 재고를 다시 확인해 주세요."); }
        var row = new MaterialConsumption(); row.setWorkOrderId(command.workOrderId()); row.setRawLotId(command.rawLotId()); row.setBomComponentId(command.bomComponentId());
        row.setReceiptId(receipt.getReceiptId()); row.setConsumedQty(command.quantity()); row.setCreatedBy(command.actor());
        requireOne(mapper.insert(row)); if (row.getConsumptionId() == null) { throw invalid("Consumption insert did not return an ID."); }
        MaterialConsumption stored = mapper.findByReceipt(receipt.getReceiptId());
        if (stored == null || !Objects.equals(stored.getConsumptionId(), row.getConsumptionId()) || stored.getConsumedAt() == null
                || !Objects.equals(stored.getReceiptId(), receipt.getReceiptId()) || !Objects.equals(stored.getWorkOrderId(), command.workOrderId())
                || !Objects.equals(stored.getBomComponentId(), command.bomComponentId()) || !Objects.equals(stored.getRawLotId(), command.rawLotId())
                || !Objects.equals(stored.getUnitCode(), lot.getUnitCode())
                || stored.getConsumedQty().compareTo(command.quantity()) != 0 || !Objects.equals(stored.getCreatedBy(), command.actor())) { throw invalid("Stored consumption does not match the command."); }
        var outcome = MaterialConsumptionOutcome.from(stored, before, after);
        String snapshot = outcomes.encode(outcome); outcomes.decode(snapshot, stored);
        requireOne(mapper.finishReceipt(receipt.getReceiptId(), snapshot));
        if (mapper.countReceiptResults(receipt.getReceiptId()) != 1) { throw invalid("Receipt must reference exactly one business result."); }
        return outcome;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW, isolation=Isolation.READ_COMMITTED, readOnly=true)
    public MaterialConsumptionOutcome replay(MaterialConsumptionCommand command) {
        var receipt = mapper.findReceipt(command.scopeKey(), command.requestKey());
        if (receipt == null || receipt.getProcessedAt() == null || receipt.getSnapshot() == null
                || !"MATERIAL_CONSUME".equals(receipt.getOperationCode()) || !Objects.equals(receipt.getCreatedBy(), command.actor())) {
            throw invalid("No complete consumption receipt is available after UNIQUE conflict.");
        }
        if (receipt.getPayloadFingerprint() == null || !MessageDigest.isEqual(receipt.getPayloadFingerprint(), command.fingerprint())) {
            throw rule("requestKey", "이미 처리한 요청 키에 다른 작업지시·BOM 구성·LOT·수량을 사용할 수 없습니다. 새 소비 작업은 신규 등록 화면에서 시작해 주세요.");
        }
        var row = mapper.findByReceipt(receipt.getReceiptId());
        if (row == null || !Objects.equals(row.getWorkOrderId(), command.workOrderId()) || !Objects.equals(row.getBomComponentId(), command.bomComponentId())
                || !Objects.equals(row.getRawLotId(), command.rawLotId()) || row.getConsumedQty().compareTo(command.quantity()) != 0
                || !Objects.equals(row.getCreatedBy(), command.actor()) || mapper.countReceiptResults(receipt.getReceiptId()) != 1) {
            throw invalid("Consumption receipt does not identify exactly one matching result.");
        }
        return outcomes.decode(receipt.getSnapshot(), row);
    }
    private void requireOne(int count) { if (count != 1) { throw invalid("Consumption write must affect exactly one row."); } }
    private DataIntegrityViolationException invalid(String message) { return new DataIntegrityViolationException(message); }
    private MaterialConsumptionRuleException rule(String field, String message) { return new MaterialConsumptionRuleException(field, message); }
}
