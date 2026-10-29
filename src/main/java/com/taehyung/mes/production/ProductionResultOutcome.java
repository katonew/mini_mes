package com.taehyung.mes.production;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.LocalDateTime;

@JsonPropertyOrder({"version", "productionResultId", "workOrderId", "finishedLotId", "lotNo", "producedQty", "goodQty", "defectQty", "workerRef", "producedAt"})
public record ProductionResultOutcome(int version, Long productionResultId, Long workOrderId, Long finishedLotId,
                                      String lotNo, String producedQty, String goodQty, String defectQty,
                                      String workerRef, @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'") LocalDateTime producedAt) {
    static ProductionResultOutcome from(ProductionResult row) {
        return new ProductionResultOutcome(1, row.getProductionResultId(), row.getWorkOrderId(), row.getFinishedLotId(),
                row.getLotNo(), row.getProducedQty().stripTrailingZeros().toPlainString(), row.getGoodQty().stripTrailingZeros().toPlainString(),
                row.getDefectQty().stripTrailingZeros().toPlainString(), row.getWorkerRef(), row.getProducedAt());
    }
}
