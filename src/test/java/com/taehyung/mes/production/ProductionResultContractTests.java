package com.taehyung.mes.production;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProductionResultContractTests {
    private ProductionResultForm form(String lotNo, String produced, String good, String defect) {
        var form = new ProductionResultForm(); form.setWorkOrderId(1L); form.setLotNo(lotNo);
        form.setProducedQty(new BigDecimal(produced)); form.setGoodQty(new BigDecimal(good));
        form.setDefectQty(new BigDecimal(defect)); form.setWorkerRef("worker A"); form.setRequestKey(UUID.randomUUID().toString()); return form;
    }
    @Test void firstAndLaterPayloadsAreDistinctAndNumbersAreNormalized() {
        var first = ProductionResultCommand.from(form("FG-1", "1.000000", "1", "0"), "ACTOR");
        var equivalent = ProductionResultCommand.from(form("FG-1", "1", "1.000000", "0.000000"), "ACTOR");
        var later = ProductionResultCommand.from(form(null, "1", "1", "0"), "ACTOR");
        assertThat(first.fingerprint()).containsExactly(equivalent.fingerprint());
        assertThat(first.fingerprint()).isNotEqualTo(later.fingerprint());
        assertThat(first.producedQty()).isEqualByComparingTo("1");
        assertThat(first.lotNo()).isEqualTo("FG-1");
        assertThat(later.lotNo()).isNull();
    }
    @Test void quantitiesAndIdentityAreValidatedWithoutRounding() {
        for (String value : new String[]{"0", "-1", "1.5", "1.0000001", "1000000000000"}) {
            assertThatThrownBy(() -> ProductionResultCommand.from(form("FG-1", value, value, "0"), "ACTOR"))
                    .isInstanceOf(ProductionResultRuleException.class);
        }
        assertThatThrownBy(() -> ProductionResultCommand.from(form("FG-1", "2", "1", "0"), "ACTOR"))
                .isInstanceOf(ProductionResultRuleException.class);
        assertThatThrownBy(() -> ProductionResultCommand.from(form("한글", "1", "1", "0"), "ACTOR"))
                .isInstanceOf(ProductionResultRuleException.class);
        var blankWorker = form("FG-1", "1", "1", "0"); blankWorker.setWorkerRef(" ");
        assertThatThrownBy(() -> ProductionResultCommand.from(blankWorker, "ACTOR"))
                .isInstanceOf(ProductionResultRuleException.class);
    }
    @Test void workerAndLotTextArePreservedExactlyInTheFingerprint() {
        var base = form("FG-a", "1", "1", "0");
        var changed = form("FG-A", "1", "1", "0");
        assertThat(ProductionResultCommand.from(base, "ACTOR").fingerprint())
                .isNotEqualTo(ProductionResultCommand.from(changed, "ACTOR").fingerprint());
        changed.setLotNo("FG-a"); changed.setWorkerRef("Worker A");
        assertThat(ProductionResultCommand.from(base, "ACTOR").fingerprint())
                .isNotEqualTo(ProductionResultCommand.from(changed, "ACTOR").fingerprint());
    }
}
