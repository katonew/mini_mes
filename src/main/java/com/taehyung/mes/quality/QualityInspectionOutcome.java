package com.taehyung.mes.quality;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public record QualityInspectionOutcome(int version, Long inspectionId, Long finishedLotId, Long workOrderId,
                                       Long roundNo, String inspectedQty, String acceptedQty, String rejectedQty,
                                       String inspectorRef, String judgementReason,
                                       @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'") LocalDateTime inspectedAt) {
    static QualityInspectionOutcome from(QualityInspection row) {
        return new QualityInspectionOutcome(1, row.getInspectionId(), row.getFinishedLotId(), row.getWorkOrderId(),
                row.getRoundNo(), row.getInspectedQty().stripTrailingZeros().toPlainString(),
                row.getAcceptedQty().stripTrailingZeros().toPlainString(), row.getRejectedQty().stripTrailingZeros().toPlainString(),
                row.getInspectorRef(), row.getJudgementReason(), row.getInspectedAt());
    }
}
