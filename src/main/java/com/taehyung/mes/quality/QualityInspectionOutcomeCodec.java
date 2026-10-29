package com.taehyung.mes.quality;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class QualityInspectionOutcomeCodec {
    private final ObjectMapper json;
    public QualityInspectionOutcomeCodec(ObjectMapper json) { this.json = json; }
    public String encode(QualityInspectionOutcome outcome) {
        try { return json.writeValueAsString(outcome); }
        catch (JsonProcessingException exception) { throw new DataIntegrityViolationException("Cannot encode inspection outcome.", exception); }
    }
    public QualityInspectionOutcome decode(String snapshot, QualityInspection row) {
        if (snapshot == null || row == null) { throw new DataIntegrityViolationException("Missing inspection receipt result."); }
        try {
            var outcome = json.readValue(snapshot, QualityInspectionOutcome.class);
            if (!QualityInspectionOutcome.from(row).equals(outcome)) { throw new DataIntegrityViolationException("Inspection receipt and result are inconsistent."); }
            return outcome;
        } catch (JsonProcessingException exception) { throw new DataIntegrityViolationException("Invalid inspection success snapshot.", exception); }
    }
}
