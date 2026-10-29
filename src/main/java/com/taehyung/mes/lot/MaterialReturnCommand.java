package com.taehyung.mes.lot;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** 동일한 검증·정규화 입력을 해시와 반환 저장에 함께 사용한다. */
public record MaterialReturnCommand(Long consumptionId, BigDecimal quantity, String reason,
                                    String requestKey, String actor, String scopeKey) {
    static final BigDecimal MAX_QTY = new BigDecimal("999999999999.999999");
    public static MaterialReturnCommand from(MaterialReturnForm form, String actor) {
        if (form == null) { throw rule(null, "반환 입력값이 필요합니다."); }
        if (form.getConsumptionId() == null || form.getConsumptionId() <= 0) { throw rule("consumptionId", "반환할 원소비 이력을 선택해 주세요."); }
        BigDecimal quantity = form.getReturnedQty();
        if (quantity == null || quantity.signum() <= 0 || quantity.scale() > 6 || quantity.compareTo(MAX_QTY) > 0) {
            throw rule("returnedQty", "반환수량은 양수·소수 6자리 이내이며 999999999999.999999 이하여야 합니다.");
        }
        String reason = form.getReason();
        if (reason == null || reason.isBlank() || reason.codePointCount(0, reason.length()) > 500) { throw rule("reason", "반환사유는 공백이 아닌 500자 이하로 입력해 주세요."); }
        if (actor == null || actor.isBlank() || actor.codePointCount(0, actor.length()) > 64) { throw rule(null, "MES_ACTOR_ID는 공백이 아닌 64자 이하로 설정해야 합니다."); }
        String key = form.getRequestKey();
        if (key == null || !key.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) { throw rule("requestKey", "요청 키가 유효한 UUID가 아닙니다. 원소비 상세에서 새 반환을 시작해 주세요."); }
        return new MaterialReturnCommand(form.getConsumptionId(), new BigDecimal(quantity.stripTrailingZeros().toPlainString()), reason,
                UUID.fromString(key).toString(), actor, HexFormat.of().formatHex(sha256(actor)));
    }
    public byte[] fingerprint() {
        return sha256("MATERIAL_RETURN:v1\nconsumptionId=" + consumptionId + "\nreturnedQty=" + quantity.toPlainString() + "\nreason=" + reason);
    }
    private static byte[] sha256(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is required.", exception); }
    }
    private static MaterialReturnRuleException rule(String field, String message) { return new MaterialReturnRuleException(field, message); }
}
