package com.taehyung.mes.lot;

import com.taehyung.mes.common.actor.ActorProvider;
import com.taehyung.mes.workorder.WorkOrder;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaterialConsumptionService {
    private final MaterialConsumptionMapper mapper;
    private final MaterialConsumptionTransactionService transactions;
    private final ActorProvider actors;
    private final MaterialConsumptionOutcomeCodec outcomes;
    public MaterialConsumptionService(MaterialConsumptionMapper mapper, MaterialConsumptionTransactionService transactions, ActorProvider actors, MaterialConsumptionOutcomeCodec outcomes) {
        this.mapper = mapper; this.transactions = transactions; this.actors = actors; this.outcomes = outcomes;
    }
    public MaterialConsumptionOutcome create(MaterialConsumptionForm form) {
        var command = MaterialConsumptionCommand.from(form, actors.getActorId());
        try { return transactions.create(command); }
        catch (MaterialConsumptionRequestExistsException exception) {
            // create의 프록시가 롤백을 마친 후 별도 프록시 호출의 새 트랜잭션에서 확인한다.
            return transactions.replay(command);
        }
    }
    @Transactional(readOnly=true)
    public List<MaterialConsumption> list(String workOrderId, String lotNo, String keyword) {
        if (lotNo == null || lotNo.length() > 64 || keyword == null || keyword.codePointCount(0, keyword.length()) > 200) {
            throw new MaterialConsumptionRuleException(null, "LOT 번호는 64자, 원자재 검색어는 200자 이내로 입력해 주세요.");
        }
        return mapper.findAll(searchId(workOrderId), lotNo, keyword);
    }
    @Transactional(readOnly=true)
    public List<WorkOrder> orders() { return mapper.findWorkOrders(); }

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public MaterialConsumptionOptions options(Long workOrderId, Long componentId) {
        if (workOrderId == null || workOrderId <= 0) { throw rule("작업지시를 선택해 주세요."); }
        WorkOrder order = mapper.findWorkOrder(workOrderId);
        if (order == null) { throw rule("작업지시를 찾을 수 없습니다."); }
        List<MaterialConsumptionMaterial> materials = mapper.findMaterials(workOrderId);
        if (materials.isEmpty() || materials.stream().anyMatch(row -> row.getBaseQty().signum() <= 0 || row.getMaterialRequiredQty().signum() <= 0)) {
            throw rule("확정된 BOM 구성과 기준수량을 확인해 주세요.");
        }
        boolean selectedComponentExists = componentId != null && materials.stream().anyMatch(row -> row.getBomComponentId().equals(componentId));
        var options = new MaterialConsumptionOptions(); options.setOrder(order); options.setMaterials(materials);
        options.setLots(selectedComponentExists ? mapper.findLots(workOrderId, componentId) : List.of()); return options;
    }
    @Transactional(readOnly=true)
    public MaterialConsumption get(Long consumptionId) {
        if (consumptionId == null || consumptionId <= 0) { throw new MaterialConsumptionNotFoundException(); }
        var row = mapper.findById(consumptionId);
        if (row == null) { throw new MaterialConsumptionNotFoundException(); }
        row.setOutcome(outcomes.decode(row.getSnapshot(), row)); return row;
    }
    private Long searchId(String value) {
        if (value != null && value.isEmpty()) { return null; }
        if (value == null || !value.matches("[0-9]{1,19}")) { throw rule("작업지시 ID는 양의 정수로 입력해 주세요."); }
        try { long id = Long.parseLong(value); if (id > 0) { return id; } }
        catch (NumberFormatException exception) { throw rule("작업지시 ID가 조회 가능한 범위를 초과했습니다."); }
        throw rule("작업지시 ID는 양의 정수로 입력해 주세요.");
    }
    private MaterialConsumptionRuleException rule(String message) { return new MaterialConsumptionRuleException(null, message); }
}
