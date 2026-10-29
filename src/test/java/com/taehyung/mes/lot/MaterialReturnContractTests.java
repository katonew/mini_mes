package com.taehyung.mes.lot;

import java.math.BigDecimal;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MaterialReturnContractTests {
    static MaterialReturnForm form(String quantity) {
        var form = new MaterialReturnForm(); form.setConsumptionId(7L); form.setReturnedQty(new BigDecimal(quantity));
        form.setReason("미사용 자재 반환"); form.setRequestKey("123e4567-e89b-12d3-a456-426614174000"); return form;
    }
    @Test void canonicalQuantityAndUuidRetainTheSameRequestIdentity() {
        var first = MaterialReturnCommand.from(form("1"), "DEV");
        var form = form("1.000000"); form.setRequestKey(form.getRequestKey().toUpperCase());
        var second = MaterialReturnCommand.from(form, "DEV");
        assertThat(second.quantity().toPlainString()).isEqualTo("1");
        assertThat(second.requestKey()).isEqualTo(first.requestKey()); assertThat(second.fingerprint()).isEqualTo(first.fingerprint());
        assertThat(first.scopeKey()).hasSize(64);
        assertThat(HexFormat.of().formatHex(first.fingerprint())).isEqualTo("d7aab6e21e3ce8eac51cc9d814e6ef2e01d7aba426194c90eaf97b07091b6001");
    }
    @Test void differentSourceQuantityReasonOrActorCannotReplayTheSamePayload() {
        var first = MaterialReturnCommand.from(form("1"), "DEV");
        var changed = form("1"); changed.setConsumptionId(8L);
        assertThat(MaterialReturnCommand.from(changed,"DEV").fingerprint()).isNotEqualTo(first.fingerprint());
        assertThat(MaterialReturnCommand.from(form("2"),"DEV").fingerprint()).isNotEqualTo(first.fingerprint());
        changed = form("1"); changed.setReason("미사용 자재 반환 ");
        assertThat(MaterialReturnCommand.from(changed,"DEV").fingerprint()).isNotEqualTo(first.fingerprint());
        assertThat(MaterialReturnCommand.from(form("1"),"OTHER").scopeKey()).isNotEqualTo(first.scopeKey());
    }
    @Test void invalidInputsAreRejectedBeforeCanonicalization() {
        for (String quantity : new String[]{"0","-1","0.0000001","1.0000000","1000000000000"}) {
            assertThatThrownBy(() -> MaterialReturnCommand.from(form(quantity),"DEV")).isInstanceOf(MaterialReturnRuleException.class);
        }
        for (String reason : new String[]{"", "  ", "가".repeat(501)}) {
            var form = form("1"); form.setReason(reason);
            assertThatThrownBy(() -> MaterialReturnCommand.from(form,"DEV")).isInstanceOf(MaterialReturnRuleException.class);
        }
        var form = form("1"); form.setReason("😀".repeat(500));
        assertThat(MaterialReturnCommand.from(form,"DEV").reason()).isEqualTo(form.getReason());
        form.setRequestKey("bad"); assertThatThrownBy(() -> MaterialReturnCommand.from(form,"DEV")).isInstanceOf(MaterialReturnRuleException.class);
    }
}
