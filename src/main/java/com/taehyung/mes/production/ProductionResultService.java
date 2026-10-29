package com.taehyung.mes.production;

import com.taehyung.mes.common.actor.ActorProvider;
import com.taehyung.mes.workorder.WorkOrder;
import com.taehyung.mes.workorder.WorkOrderMapper;
import java.util.List;
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
    @Transactional(readOnly=true)
    public ProductionResult get(Long resultId) {
        if (resultId == null || resultId <= 0) { throw new ProductionResultNotFoundException(); }
        var row = mapper.findById(resultId);
        if (row == null) { throw new ProductionResultNotFoundException(); }
        row.setOutcome(outcomes.decode(row.getSnapshot(), row)); return row;
    }
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
