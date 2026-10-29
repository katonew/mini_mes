package com.taehyung.mes.production;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.List;
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
    public ProductionResultOutcome decodeInitial(String snapshot, ProductionResult current, List<ProductionResultCorrection> history) {
        if (current == null || current.getRevisionNo() == null || current.getRevisionNo() <= 0 || history == null
                || current.getRevisionNo() - 1 != history.size()) { throw invalid(); }
        var original = new ProductionResult(); original.setProductionResultId(current.getProductionResultId());
        original.setWorkOrderId(current.getWorkOrderId()); original.setFinishedLotId(current.getFinishedLotId()); original.setLotNo(current.getLotNo());
        if (history.isEmpty()) {
            original.setProducedQty(current.getProducedQty()); original.setGoodQty(current.getGoodQty()); original.setDefectQty(current.getDefectQty());
            original.setProducedAt(current.getProducedAt()); original.setWorkerRef(current.getWorkerRef());
        } else {
            var first = history.get(0);
            original.setProducedQty(first.getBeforeProducedQty()); original.setGoodQty(first.getBeforeGoodQty()); original.setDefectQty(first.getBeforeDefectQty());
            original.setProducedAt(first.getBeforeProducedAt()); original.setWorkerRef(first.getBeforeWorkerRef());
        }
        var initial = decode(snapshot, original);
        BigDecimal produced = original.getProducedQty(), good = original.getGoodQty(), defect = original.getDefectQty();
        long revision = 1;
        for (var correction : history) {
            if (!Objects.equals(correction.getProductionResultId(), current.getProductionResultId())
                    || !Objects.equals(correction.getFromRevision(), revision) || !Objects.equals(correction.getToRevision(), revision + 1)
                    || !same(produced, correction.getBeforeProducedQty()) || !same(good, correction.getBeforeGoodQty()) || !same(defect, correction.getBeforeDefectQty())
                    || !Objects.equals(original.getProducedAt(), correction.getBeforeProducedAt())
                    || !Objects.equals(original.getProducedAt(), correction.getAfterProducedAt())
                    || !Objects.equals(original.getWorkerRef(), correction.getBeforeWorkerRef())
                    || !Objects.equals(original.getWorkerRef(), correction.getAfterWorkerRef())
                    || correction.getAfterProducedQty() == null || correction.getAfterGoodQty() == null || correction.getAfterDefectQty() == null
                    || correction.getAfterProducedQty().signum() <= 0 || correction.getAfterGoodQty().signum() < 0 || correction.getAfterDefectQty().signum() < 0
                    || correction.getAfterProducedQty().compareTo(correction.getAfterGoodQty().add(correction.getAfterDefectQty())) != 0) { throw invalid(); }
            produced = correction.getAfterProducedQty(); good = correction.getAfterGoodQty(); defect = correction.getAfterDefectQty(); revision++;
        }
        if (!same(produced, current.getProducedQty()) || !same(good, current.getGoodQty()) || !same(defect, current.getDefectQty())
                || !Objects.equals(original.getProducedAt(), current.getProducedAt()) || !Objects.equals(original.getWorkerRef(), current.getWorkerRef())) { throw invalid(); }
        return initial;
    }
    private boolean same(BigDecimal left, BigDecimal right) { return left != null && right != null && left.compareTo(right) == 0; }
    private DataIntegrityViolationException invalid() { return new DataIntegrityViolationException("Production receipt, correction history and current result are inconsistent."); }
}
