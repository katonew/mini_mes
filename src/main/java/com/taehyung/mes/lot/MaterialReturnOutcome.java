package com.taehyung.mes.lot;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonPropertyOrder({"version","returnId","consumptionId","workOrderId","rawLotId","returnedQty","unitCode","reason","returnedAt","availableBefore","availableAfter"})
public record MaterialReturnOutcome(@JsonProperty("version") int version,
                                    @JsonProperty("returnId") Long returnId,
                                    @JsonProperty("consumptionId") Long consumptionId,
                                    @JsonProperty("workOrderId") Long workOrderId,
                                    @JsonProperty("rawLotId") Long rawLotId,
                                    @JsonProperty("returnedQty") String returnedQty,
                                    @JsonProperty("unitCode") String unitCode,
                                    @JsonProperty("reason") String reason,
                                    @JsonProperty("returnedAt") @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'") LocalDateTime returnedAt,
                                    @JsonProperty("availableBefore") String availableBefore,
                                    @JsonProperty("availableAfter") String availableAfter) {
    static MaterialReturnOutcome from(MaterialReturn row, BigDecimal before, BigDecimal after) {
        return new MaterialReturnOutcome(1,row.getReturnId(),row.getConsumptionId(),row.getWorkOrderId(),row.getRawLotId(),plain(row.getReturnedQty()),
                row.getUnitCode(),row.getReason(),row.getReturnedAt(),plain(before),plain(after));
    }
    private static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    public String getAvailableBefore() { return availableBefore; }
    public String getAvailableAfter() { return availableAfter; }
}
