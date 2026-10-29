package com.taehyung.mes.quality;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QualityInspectionMapper {
    List<QualityInspection> findAll(@Param("workOrderId") Long workOrderId, @Param("lotId") Long lotId,
                                    @Param("lotNo") String lotNo, @Param("keyword") String keyword);
    QualityInspection findById(@Param("inspectionId") Long inspectionId);
    QualityInspection findByReceipt(@Param("receiptId") Long receiptId);
    QualityInspectionLot findLot(@Param("lotId") Long lotId);
    List<QualityInspectionLot> findEligibleLots();
    int insert(QualityInspection row);
    int claimReceipt(QualityInspectionReceipt receipt);
    QualityInspectionReceipt findReceipt(@Param("scopeKey") String scopeKey, @Param("requestKey") String requestKey);
    int finishReceipt(@Param("receiptId") Long receiptId, @Param("snapshot") String snapshot);
    int countReceiptResults(@Param("receiptId") Long receiptId);
}
