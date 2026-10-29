package com.taehyung.mes.quality;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public record QualityInspectionCommand(Long finishedLotId, BigDecimal inspectedQty, BigDecimal acceptedQty,
                                      BigDecimal rejectedQty, String inspectorRef, String judgementReason,
                                      String requestKey, String actor, String scopeKey) {
    public static QualityInspectionCommand from(QualityInspectionForm form, String actor) {
        if (form == null) { throw rule(null, "검사 입력값이 필요합니다."); }
        if (form.getFinishedLotId() == null || form.getFinishedLotId() <= 0) { throw rule("finishedLotId", "완제품 LOT을 선택해 주세요."); }
        BigDecimal inspected = ea(form.getInspectedQty(), "inspectedQty", true);
        BigDecimal accepted = ea(form.getAcceptedQty(), "acceptedQty", false);
        BigDecimal rejected = ea(form.getRejectedQty(), "rejectedQty", false);
        if (inspected.compareTo(accepted.add(rejected)) != 0) { throw rule("inspectedQty", "검사수량은 합격수량과 불합격수량의 합계여야 합니다."); }
        String inspector = form.getInspectorRef();
        if (inspector == null || inspector.isBlank() || inspector.codePointCount(0, inspector.length()) > 64) {
            throw rule("inspectorRef", "검사자 식별자는 공백이 아닌 64자 이하로 입력해 주세요.");
        }
        String reason = form.getJudgementReason();
        if (reason != null && reason.isEmpty()) { reason = null; }
        if (reason != null && reason.codePointCount(0, reason.length()) > 500) { throw rule("judgementReason", "판정 사유는 500자 이내로 입력해 주세요."); }
        if (actor == null || actor.isBlank() || actor.codePointCount(0, actor.length()) > 64) { throw rule(null, "MES_ACTOR_ID는 공백이 아닌 64자 이하로 설정해야 합니다."); }
        String key = form.getRequestKey();
        if (key == null || !key.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw rule("requestKey", "요청 키가 유효한 UUID가 아닙니다. 신규 등록 화면에서 다시 시작해 주세요.");
        }
        return new QualityInspectionCommand(form.getFinishedLotId(), inspected, accepted, rejected, inspector,
                reason, UUID.fromString(key).toString(), actor, HexFormat.of().formatHex(sha256(actor)));
    }
    public byte[] fingerprint() {
        // 자유 입력 문자열은 길이를 포함해 구분자 삽입에 의한 payload 충돌을 방지한다.
        return sha256("QUALITY_INSPECTION:v1\nfinishedLotId=" + finishedLotId + "\ninspectedQty=" + inspectedQty.toPlainString()
                + "\nacceptedQty=" + acceptedQty.toPlainString() + "\nrejectedQty=" + rejectedQty.toPlainString()
                + "\ninspectorRef=" + inspectorRef.length() + ":" + inspectorRef
                + "\njudgementReason=" + (judgementReason == null ? "NULL" : judgementReason.length() + ":" + judgementReason));
    }
    private static BigDecimal ea(BigDecimal value, String field, boolean positive) {
        if (value == null || (positive ? value.signum() <= 0 : value.signum() < 0) || value.scale() > 6
                || value.compareTo(new BigDecimal("999999999999")) > 0 || value.stripTrailingZeros().scale() > 0) {
            throw rule(field, "검사수량은 양수, 합격·불합격은 0 이상의 EA 정수이며 각 수량은 999999999999 이하여야 합니다.");
        }
        return new BigDecimal(value.stripTrailingZeros().toPlainString());
    }
    private static byte[] sha256(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is required.", exception); }
    }
    private static QualityInspectionRuleException rule(String field, String message) { return new QualityInspectionRuleException(field, message); }
}
