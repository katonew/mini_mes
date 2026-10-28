package com.taehyung.mes.lot;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MaterialConsumptionContractTests {
    @Test void sameNumericQuantityProducesTheSameFingerprintAndStorageValue() {
        var first = MaterialConsumptionCommand.from(form("1"), "개발자");
        var retry = MaterialConsumptionCommand.from(form("1.000000"), "개발자");
        assertThat(first.quantity()).isEqualByComparingTo(retry.quantity());
        assertThat(first.fingerprint()).containsExactly(retry.fingerprint());
        assertThat(first.scopeKey()).matches("[0-9a-f]{64}");
        assertThat(java.util.Arrays.equals(MaterialConsumptionCommand.from(form("2"), "개발자").fingerprint(), first.fingerprint())).isFalse();
        var otherOrder = form("1"); otherOrder.setWorkOrderId(99L);
        var otherComponent = form("1"); otherComponent.setBomComponentId(99L);
        var otherLot = form("1"); otherLot.setRawLotId(99L);
        for (var changed : java.util.List.of(otherOrder, otherComponent, otherLot)) {
            assertThat(java.util.Arrays.equals(MaterialConsumptionCommand.from(changed, "개발자").fingerprint(), first.fingerprint())).isFalse();
        }
        assertThat(MaterialConsumptionCommand.from(form("1"), "OTHER").scopeKey()).isNotEqualTo(first.scopeKey());
    }
    @Test void validatesKeysIdsQuantityAndActorBeforeNormalization() {
        for (String quantity : new String[]{"0", "-1", "0.0000001", "1.0000000", "1000000000000"}) {
            assertThatThrownBy(() -> MaterialConsumptionCommand.from(form(quantity), "DEV")).isInstanceOf(MaterialConsumptionRuleException.class);
        }
        var badKey = form("1"); badKey.setRequestKey("bad-key");
        assertThatThrownBy(() -> MaterialConsumptionCommand.from(badKey, "DEV")).isInstanceOf(MaterialConsumptionRuleException.class);
        badKey.setRequestKey(null);
        assertThatThrownBy(() -> MaterialConsumptionCommand.from(badKey, "DEV")).isInstanceOf(MaterialConsumptionRuleException.class);
        assertThatThrownBy(() -> MaterialConsumptionCommand.from(form("1"), " ")).isInstanceOf(MaterialConsumptionRuleException.class);
        var invalid = form("1"); invalid.setRawLotId(0L);
        assertThatThrownBy(() -> MaterialConsumptionCommand.from(invalid, "DEV")).isInstanceOf(MaterialConsumptionRuleException.class);
    }
    @Test void snapshotRoundTripKeepsTheOriginalBalanceAndMicrosecondUtcTime() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules(); var codec = new MaterialConsumptionOutcomeCodec(json);
        var row = new MaterialConsumption(); row.setConsumptionId(7L); row.setWorkOrderId(1L); row.setBomComponentId(2L); row.setRawLotId(3L);
        row.setConsumedQty(new BigDecimal("1")); row.setUnitCode("EA"); row.setConsumedAt(LocalDateTime.of(2026,10,27,1,2,3,123456000));
        var original = MaterialConsumptionOutcome.from(row, new BigDecimal("10"), new BigDecimal("9"));
        String snapshot = codec.encode(original); var decoded = codec.decode(snapshot, row);
        assertThat(snapshot).contains("2026-10-27T01:02:03.123456Z");
        assertThat(new com.taehyung.mes.common.web.KstDateTimeFormatter().formatUtc(decoded.consumedAt(), "yyyy-MM-dd HH:mm:ss.SSSSSS")).isEqualTo("2026-10-27 10:02:03.123456");
        assertThat(decoded).isEqualTo(original); assertThat(decoded.availableBefore()).isEqualTo("10");
        row.setAvailableQty(BigDecimal.ZERO);
        assertThat(codec.decode(snapshot, row).availableAfter()).isEqualTo("9");
        row.setConsumedQty(new BigDecimal("2"));
        assertThatThrownBy(() -> codec.decode(snapshot, row)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void expectedBomQuantityIsExactEvenWhenTheDecimalDoesNotTerminate() {
        var material = new MaterialConsumptionMaterial(); material.setOrderedQty(new BigDecimal("10")); material.setMaterialRequiredQty(BigDecimal.ONE);
        material.setBaseQty(new BigDecimal("3")); assertThat(material.getExpectedQtyDisplay()).isEqualTo("10 / 3");
        material.setBaseQty(new BigDecimal("4")); assertThat(material.getExpectedQtyDisplay()).isEqualTo("2.5");
        material.setConsumedQty(BigDecimal.ONE); material.setReturnedQty(BigDecimal.ZERO);
        assertThat(material.getDifferenceDisplay()).isEqualTo("-1.5");
        material.setBaseQty(new BigDecimal("3")); assertThat(material.getDifferenceDisplay()).isEqualTo("-7 / 3");
    }
    static MaterialConsumptionForm form(String quantity) {
        var form = new MaterialConsumptionForm(); form.setWorkOrderId(1L); form.setBomComponentId(2L); form.setRawLotId(3L);
        form.setConsumedQty(new BigDecimal(quantity)); form.setRequestKey(UUID.fromString("12345678-1234-1234-1234-123456789abc").toString()); return form;
    }
}
