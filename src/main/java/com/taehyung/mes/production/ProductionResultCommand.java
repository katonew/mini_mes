package com.taehyung.mes.production;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** 입력 원문은 보존하고 수량과 UUID만 정규화해 저장값과 fingerprint를 일치시킨다. */
public record ProductionResultCommand(Long workOrderId, String lotNo, BigDecimal producedQty, BigDecimal goodQty,
                                      BigDecimal defectQty, String workerRef, String requestKey, String actor, String scopeKey) {
    private static final BigDecimal MAX_EA = new BigDecimal("999999999999");
    public static ProductionResultCommand from(ProductionResultForm form, String actor) {
        if (form == null) { throw rule(null, "생산실적 입력값이 필요합니다."); }
        if (form.getWorkOrderId() == null || form.getWorkOrderId() <= 0) { throw rule("workOrderId", "작업지시를 선택해 주세요."); }
        String lotNo = form.getLotNo();
        if (lotNo != null && lotNo.isEmpty()) { lotNo = null; }
        if (lotNo != null && (lotNo.isBlank() || lotNo.length() > 64 || lotNo.codePoints().anyMatch(c -> c < 32 || c > 126))) {
            throw rule("lotNo", "완제품 LOT 번호는 공백만으로 구성되지 않은 표시 가능한 ASCII 1~64자로 입력해 주세요.");
        }
        BigDecimal produced = ea(form.getProducedQty(), "producedQty", true);
        BigDecimal good = ea(form.getGoodQty(), "goodQty", false);
        BigDecimal defect = ea(form.getDefectQty(), "defectQty", false);
        if (produced.compareTo(good.add(defect)) != 0) { throw rule("producedQty", "생산수량은 양품수량과 불량수량의 합계여야 합니다."); }
        String worker = form.getWorkerRef();
        if (worker == null || worker.isBlank() || worker.codePointCount(0, worker.length()) > 64) {
            throw rule("workerRef", "작업자 식별자는 공백이 아닌 64자 이하로 입력해 주세요.");
        }
        if (actor == null || actor.isBlank() || actor.codePointCount(0, actor.length()) > 64) { throw rule(null, "MES_ACTOR_ID는 공백이 아닌 64자 이하로 설정해야 합니다."); }
        String key = form.getRequestKey();
        if (key == null || !key.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw rule("requestKey", "요청 키가 유효한 UUID가 아닙니다. 신규 등록 화면에서 다시 시작해 주세요.");
        }
        return new ProductionResultCommand(form.getWorkOrderId(), lotNo, produced, good, defect, worker,
                UUID.fromString(key).toString(), actor, HexFormat.of().formatHex(sha256(actor)));
    }
    public byte[] fingerprint() {
        String lotPart = lotNo == null ? "mode=EXISTING" : "mode=FIRST\nlotNo=" + lotNo;
        return sha256("PRODUCTION_RESULT:v1\nworkOrderId=" + workOrderId + "\n" + lotPart
                + "\nproducedQty=" + producedQty.toPlainString() + "\ngoodQty=" + goodQty.toPlainString()
                + "\ndefectQty=" + defectQty.toPlainString() + "\nworkerRef=" + workerRef);
    }
    private static BigDecimal ea(BigDecimal value, String field, boolean positive) {
        if (value == null || (positive ? value.signum() <= 0 : value.signum() < 0) || value.scale() > 6
                || value.compareTo(MAX_EA) > 0 || value.stripTrailingZeros().scale() > 0) {
            throw rule(field, "생산·양품·불량수량은 EA 정수이며 생산수량은 양수, 각 수량은 999999999999 이하여야 합니다.");
        }
        return new BigDecimal(value.stripTrailingZeros().toPlainString());
    }
    private static byte[] sha256(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is required.", exception); }
    }
    private static ProductionResultRuleException rule(String field, String message) { return new ProductionResultRuleException(field, message); }
}
