package com.taehyung.mes.lot;

import com.taehyung.mes.workorder.WorkOrder;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MaterialConsumptionMapper {
    List<MaterialConsumption> findAll(@Param("workOrderId") Long workOrderId, @Param("lotNo") String lotNo, @Param("keyword") String keyword);
    MaterialConsumption findById(@Param("consumptionId") Long consumptionId);
    MaterialConsumption findByReceipt(@Param("receiptId") Long receiptId);
    List<WorkOrder> findWorkOrders();
    WorkOrder findWorkOrder(@Param("workOrderId") Long workOrderId);
    List<MaterialConsumptionMaterial> findMaterials(@Param("workOrderId") Long workOrderId);
    List<RawMaterialLot> findLots(@Param("workOrderId") Long workOrderId, @Param("bomComponentId") Long bomComponentId);
    RawMaterialLot findLotForUpdate(@Param("rawLotId") Long rawLotId);
    int decreaseAvailable(@Param("rawLotId") Long rawLotId, @Param("quantity") BigDecimal quantity);
    int insert(MaterialConsumption consumption);
    int claimReceipt(MaterialConsumptionReceipt receipt);
    MaterialConsumptionReceipt findReceipt(@Param("scopeKey") String scopeKey, @Param("requestKey") String requestKey);
    int finishReceipt(@Param("receiptId") Long receiptId, @Param("snapshot") String snapshot);
    int countReceiptResults(@Param("receiptId") Long receiptId);
}
