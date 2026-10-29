package com.taehyung.mes.production;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class ProductionResultOutcomeCodec {
    private final ObjectMapper json;
    public ProductionResultOutcomeCodec(ObjectMapper json) { this.json = json; }
    public String encode(ProductionResultOutcome outcome) {
        try { return json.writeValueAsString(outcome); }
        catch (JsonProcessingException exception) { throw new DataIntegrityViolationException("Cannot encode production outcome.", exception); }
    }
    public ProductionResultOutcome decode(String snapshot, ProductionResult row) {
        if (snapshot == null) { throw invalid(); }
        try {
            var outcome = json.readValue(snapshot, ProductionResultOutcome.class);
            if (outcome == null || outcome.version() != 1 || !Objects.equals(outcome.productionResultId(), row.getProductionResultId())
                    || !Objects.equals(outcome.workOrderId(), row.getWorkOrderId()) || !Objects.equals(outcome.finishedLotId(), row.getFinishedLotId())
                    || !Objects.equals(outcome.lotNo(), row.getLotNo()) || !Objects.equals(outcome.workerRef(), row.getWorkerRef())
                    || !Objects.equals(outcome.producedAt(), row.getProducedAt())
                    || new BigDecimal(outcome.producedQty()).compareTo(row.getProducedQty()) != 0
                    || new BigDecimal(outcome.goodQty()).compareTo(row.getGoodQty()) != 0
                    || new BigDecimal(outcome.defectQty()).compareTo(row.getDefectQty()) != 0) { throw invalid(); }
            return outcome;
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException exception) {
            throw new DataIntegrityViolationException("Invalid production success snapshot.", exception);
        }
    }
    private DataIntegrityViolationException invalid() { return new DataIntegrityViolationException("Production receipt and result are inconsistent."); }
}
