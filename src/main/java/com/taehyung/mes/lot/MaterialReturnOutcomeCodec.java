package com.taehyung.mes.lot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class MaterialReturnOutcomeCodec {
    private final ObjectMapper json;
    public MaterialReturnOutcomeCodec(ObjectMapper json) { this.json=json; }
    public String encode(MaterialReturnOutcome outcome) {
        try { return json.writeValueAsString(outcome); }
        catch (JsonProcessingException exception) { throw new DataIntegrityViolationException("Cannot encode return outcome.",exception); }
    }
    public MaterialReturnOutcome decode(String snapshot,MaterialReturn row) {
        if (snapshot==null) { throw invalid(); }
        try {
            var outcome=json.readValue(snapshot,MaterialReturnOutcome.class);
            if (outcome==null || outcome.version()!=1 || !Objects.equals(outcome.returnId(),row.getReturnId()) || !Objects.equals(outcome.consumptionId(),row.getConsumptionId())
                    || !Objects.equals(outcome.workOrderId(),row.getWorkOrderId()) || !Objects.equals(outcome.rawLotId(),row.getRawLotId())
                    || !Objects.equals(outcome.unitCode(),row.getUnitCode()) || !Objects.equals(outcome.reason(),row.getReason()) || !Objects.equals(outcome.returnedAt(),row.getReturnedAt())) { throw invalid(); }
            BigDecimal quantity=new BigDecimal(outcome.returnedQty()), before=new BigDecimal(outcome.availableBefore()), after=new BigDecimal(outcome.availableAfter());
            if (quantity.signum()<=0 || quantity.scale()>6 || quantity.compareTo(row.getReturnedQty())!=0 || before.signum()<0
                    || after.compareTo(MaterialReturnCommand.MAX_QTY)>0 || before.add(quantity).compareTo(after)!=0) { throw invalid(); }
            return outcome;
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException exception) { throw new DataIntegrityViolationException("Invalid return success snapshot.",exception); }
    }
    private DataIntegrityViolationException invalid() { return new DataIntegrityViolationException("Return receipt and result are inconsistent."); }
}
