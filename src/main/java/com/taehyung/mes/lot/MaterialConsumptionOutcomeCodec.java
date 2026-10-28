package com.taehyung.mes.lot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class MaterialConsumptionOutcomeCodec {
    private final ObjectMapper json;
    public MaterialConsumptionOutcomeCodec(ObjectMapper json) { this.json = json; }
    public String encode(MaterialConsumptionOutcome outcome) {
        try { return json.writeValueAsString(outcome); }
        catch (JsonProcessingException exception) { throw new DataIntegrityViolationException("Cannot encode consumption outcome.", exception); }
    }
    public MaterialConsumptionOutcome decode(String snapshot, MaterialConsumption row) {
        if (snapshot == null) { throw invalid(); }
        try {
            var outcome = json.readValue(snapshot, MaterialConsumptionOutcome.class);
            if (outcome == null || outcome.version() != 1 || !Objects.equals(outcome.consumptionId(), row.getConsumptionId())
                    || !Objects.equals(outcome.workOrderId(), row.getWorkOrderId()) || !Objects.equals(outcome.bomComponentId(), row.getBomComponentId())
                    || !Objects.equals(outcome.rawLotId(), row.getRawLotId()) || !Objects.equals(outcome.unitCode(), row.getUnitCode())
                    || !Objects.equals(outcome.consumedAt(), row.getConsumedAt())) { throw invalid(); }
            BigDecimal quantity = new BigDecimal(outcome.consumedQty()), before = new BigDecimal(outcome.availableBefore()), after = new BigDecimal(outcome.availableAfter());
            if (quantity.signum() <= 0 || quantity.compareTo(row.getConsumedQty()) != 0 || after.signum() < 0 || before.subtract(quantity).compareTo(after) != 0) { throw invalid(); }
            return outcome;
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException exception) {
            throw new DataIntegrityViolationException("Invalid consumption success snapshot.", exception);
        }
    }
    private DataIntegrityViolationException invalid() { return new DataIntegrityViolationException("Consumption receipt and result are inconsistent."); }
}
