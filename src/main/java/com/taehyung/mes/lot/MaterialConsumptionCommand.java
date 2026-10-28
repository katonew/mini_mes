package com.taehyung.mes.lot;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** 검증·정규화한 값 하나를 fingerprint와 실제 저장에 함께 사용한다. */
public record MaterialConsumptionCommand(Long workOrderId, Long bomComponentId, Long rawLotId, BigDecimal quantity,
                                         String requestKey, String actor, String scopeKey) {
    public static MaterialConsumptionCommand from(MaterialConsumptionForm form, String actor) {
        if (form == null) { throw rule(null, "원자재 투입 입력값이 필요합니다."); }
        id(form.getWorkOrderId(), "workOrderId", "작업지시"); id(form.getBomComponentId(), "bomComponentId", "BOM 구성"); id(form.getRawLotId(), "rawLotId", "원자재 LOT");
        BigDecimal quantity = form.getConsumedQty();
        if (quantity == null || quantity.signum() <= 0 || quantity.scale() > 6 || quantity.compareTo(new BigDecimal("999999999999.999999")) > 0) {
            throw rule("consumedQty", "투입수량은 0보다 크고 999999999999.999999 이하이며 소수 6자리 이내여야 합니다.");
        }
        if (actor == null || actor.isBlank() || actor.codePointCount(0, actor.length()) > 64) { throw rule(null, "MES_ACTOR_ID는 공백이 아닌 64자 이하로 설정해야 합니다."); }
        String key = form.getRequestKey();
        if (key == null || !key.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw rule("requestKey", "요청 키가 유효한 UUID가 아닙니다. 새 소비 작업은 신규 등록 화면에서 시작해 주세요.");
        }
        return new MaterialConsumptionCommand(form.getWorkOrderId(), form.getBomComponentId(), form.getRawLotId(),
                new BigDecimal(quantity.stripTrailingZeros().toPlainString()), UUID.fromString(key).toString(), actor, HexFormat.of().formatHex(sha256(actor)));
    }
    public byte[] fingerprint() {
        return sha256("MATERIAL_CONSUME:v1\nworkOrderId=" + workOrderId + "\nbomComponentId=" + bomComponentId
                + "\nrawLotId=" + rawLotId + "\nconsumedQty=" + quantity.toPlainString());
    }
    private static byte[] sha256(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is required.", exception); }
    }
    private static void id(Long value, String field, String label) { if (value == null || value <= 0) { throw rule(field, "목록에서 " + label + " 항목을 선택해 주세요."); } }
    private static MaterialConsumptionRuleException rule(String field, String message) { return new MaterialConsumptionRuleException(field, message); }
}
