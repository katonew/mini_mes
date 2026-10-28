package com.taehyung.mes.lot;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonPropertyOrder({"version", "consumptionId", "workOrderId", "bomComponentId", "rawLotId", "consumedQty", "unitCode", "consumedAt", "availableBefore", "availableAfter"})
public record MaterialConsumptionOutcome(@JsonProperty("version") int version,
                                         @JsonProperty("consumptionId") Long consumptionId,
                                         @JsonProperty("workOrderId") Long workOrderId,
                                         @JsonProperty("bomComponentId") Long bomComponentId,
                                         @JsonProperty("rawLotId") Long rawLotId,
                                         @JsonProperty("consumedQty") String consumedQty,
                                         @JsonProperty("unitCode") String unitCode,
                                         @JsonProperty("consumedAt") @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'") LocalDateTime consumedAt,
                                         @JsonProperty("availableBefore") String availableBefore,
                                         @JsonProperty("availableAfter") String availableAfter) {
    static MaterialConsumptionOutcome from(MaterialConsumption row, BigDecimal before, BigDecimal after) {
        return new MaterialConsumptionOutcome(1, row.getConsumptionId(), row.getWorkOrderId(), row.getBomComponentId(), row.getRawLotId(),
                plain(row.getConsumedQty()), row.getUnitCode(), row.getConsumedAt(), plain(before), plain(after));
    }
    private static String plain(BigDecimal quantity) { return quantity.stripTrailingZeros().toPlainString(); }
    public Long getConsumptionId() { return consumptionId; }
    public String getConsumedQty() { return consumedQty; }
    public String getUnitCode() { return unitCode; }
    public LocalDateTime getConsumedAt() { return consumedAt; }
    public String getAvailableBefore() { return availableBefore; }
    public String getAvailableAfter() { return availableAfter; }
}
