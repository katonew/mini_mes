package com.taehyung.mes.production;

import java.math.BigDecimal;

public record ProductionResultCorrectionCommand(long expectedRevision, BigDecimal producedQty, BigDecimal goodQty,
                                                 BigDecimal defectQty, String reason, String actor) {
    static ProductionResultCorrectionCommand from(ProductionResultCorrectionForm form, String actor) {
        if (form == null) { throw new ProductionResultRuleException(null, "정정 입력값이 필요합니다."); }
        if (form.getExpectedRevision() == null || form.getExpectedRevision() <= 0 || form.getExpectedRevision() >= 4294967295L) {
            throw new ProductionResultRuleException("expectedRevision", "정정 버전이 유효하지 않거나 DB 최대 버전에 도달했습니다. 상세에서 다시 확인해 주세요.");
        }
        var produced = ProductionResultCommand.ea(form.getProducedQty(), "producedQty", true);
        var good = ProductionResultCommand.ea(form.getGoodQty(), "goodQty", false);
        var defect = ProductionResultCommand.ea(form.getDefectQty(), "defectQty", false);
        if (produced.compareTo(good.add(defect)) != 0) { throw new ProductionResultRuleException("producedQty", "생산수량은 양품수량과 불량수량의 합계여야 합니다."); }
        if (form.getReason() == null || form.getReason().isBlank() || form.getReason().codePointCount(0, form.getReason().length()) > 500) {
            throw new ProductionResultRuleException("reason", "정정 사유는 공백이 아닌 500자 이하로 입력해 주세요.");
        }
        if (actor == null || actor.isBlank() || actor.codePointCount(0, actor.length()) > 64) { throw new ProductionResultRuleException(null, "MES_ACTOR_ID는 공백이 아닌 64자 이하로 설정해야 합니다."); }
        return new ProductionResultCorrectionCommand(form.getExpectedRevision(), produced, good, defect, form.getReason(), actor);
    }
}
