package com.taehyung.mes.production;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductionResultMapper {
    List<ProductionResult> findAll(@Param("workOrderId") Long workOrderId, @Param("lotNo") String lotNo, @Param("keyword") String keyword);
    ProductionResult findById(@Param("resultId") Long resultId);
    ProductionResult findByReceipt(@Param("receiptId") Long receiptId);
    FinishedGoodsLot findLotByWorkOrder(@Param("workOrderId") Long workOrderId);
    FinishedGoodsLot findLotById(@Param("lotId") Long lotId);
    List<FinishedGoodsLot> findLots(@Param("workOrderId") Long workOrderId, @Param("lotNo") String lotNo, @Param("keyword") String keyword);
    List<ProductionResult> findByLot(@Param("lotId") Long lotId);
    boolean existsLotNo(@Param("lotNo") String lotNo);
    int insertLot(FinishedGoodsLot lot);
    int insert(ProductionResult row);
    int claimReceipt(ProductionResultReceipt receipt);
    ProductionResultReceipt findReceipt(@Param("scopeKey") String scopeKey, @Param("requestKey") String requestKey);
    int finishReceipt(@Param("receiptId") Long receiptId, @Param("snapshot") String snapshot);
    int countReceiptResults(@Param("receiptId") Long receiptId);
}
