package com.taehyung.mes.lot;

import com.taehyung.mes.common.actor.ActorProvider;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaterialReturnService {
    private final MaterialReturnMapper mapper;
    private final MaterialReturnTransactionService transactions;
    private final ActorProvider actors;
    private final MaterialReturnOutcomeCodec outcomes;
    public MaterialReturnService(MaterialReturnMapper mapper,MaterialReturnTransactionService transactions,ActorProvider actors,MaterialReturnOutcomeCodec outcomes) {
        this.mapper=mapper; this.transactions=transactions; this.actors=actors; this.outcomes=outcomes;
    }
    public MaterialReturnOutcome create(MaterialReturnForm form) {
        var command=MaterialReturnCommand.from(form,actors.getActorId());
        try { return transactions.create(command); }
        catch (MaterialReturnRequestExistsException exception) { return transactions.replay(command); }
    }
    @Transactional(readOnly=true)
    public List<MaterialReturn> list(String workOrderId,String consumptionId,String lotNo,String keyword) {
        if (lotNo==null || lotNo.length()>64 || keyword==null || keyword.codePointCount(0,keyword.length())>200) { throw rule("LOT 번호는 64자, 원자재 검색어는 200자 이내로 입력해 주세요."); }
        return mapper.findAll(searchId(workOrderId),searchId(consumptionId),lotNo,keyword);
    }
    @Transactional(readOnly=true)
    public MaterialConsumption source(Long consumptionId) {
        if (consumptionId==null || consumptionId<=0) { throw rule("반환할 원소비 이력을 선택해 주세요."); }
        var row=mapper.findSourceDetails(consumptionId); if (row==null) { throw rule("반환할 원소비 이력을 찾을 수 없습니다."); } return row;
    }
    @Transactional(readOnly=true)
    public MaterialReturn get(Long returnId) {
        if (returnId==null || returnId<=0) { throw new MaterialReturnNotFoundException(); }
        var row=mapper.findById(returnId); if (row==null) { throw new MaterialReturnNotFoundException(); }
        row.setOutcome(outcomes.decode(row.getSnapshot(),row)); return row;
    }
    private Long searchId(String value) {
        if ("".equals(value)) { return null; }
        if (value==null || !value.matches("[0-9]{1,19}")) { throw rule("작업지시·원소비 ID는 양의 정수로 입력해 주세요."); }
        try { long id=Long.parseLong(value); if (id>0) { return id; } }
        catch (NumberFormatException exception) { throw rule("검색 ID가 조회 가능한 범위를 초과했습니다."); }
        throw rule("작업지시·원소비 ID는 양의 정수로 입력해 주세요.");
    }
    private MaterialReturnRuleException rule(String message) { return new MaterialReturnRuleException(null,message); }
}
