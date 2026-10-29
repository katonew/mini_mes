package com.taehyung.mes.lot;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MaterialReturnMapper {
    List<MaterialReturn> findAll(@Param("workOrderId") Long workOrderId, @Param("consumptionId") Long consumptionId, @Param("lotNo") String lotNo, @Param("keyword") String keyword);
    MaterialReturn findById(@Param("returnId") Long returnId);
    MaterialReturn findByReceipt(@Param("receiptId") Long receiptId);
    MaterialConsumption findConsumption(@Param("consumptionId") Long consumptionId);
    MaterialConsumption findSourceDetails(@Param("consumptionId") Long consumptionId);
    MaterialConsumption findConsumptionForUpdate(@Param("consumptionId") Long consumptionId);
    RawMaterialLot findLotForUpdate(@Param("rawLotId") Long rawLotId);
    BigDecimal sumReturned(@Param("consumptionId") Long consumptionId);
    int increaseAvailable(@Param("rawLotId") Long rawLotId, @Param("quantity") BigDecimal quantity);
    int insert(MaterialReturn row);
    int claimReceipt(MaterialReturnReceipt receipt);
    MaterialReturnReceipt findReceipt(@Param("scopeKey") String scopeKey, @Param("requestKey") String requestKey);
    int finishReceipt(@Param("receiptId") Long receiptId, @Param("snapshot") String snapshot);
    int countReceiptResults(@Param("receiptId") Long receiptId);
}
