package com.taehyung.mes.dashboard;

import com.taehyung.mes.workorder.WorkOrder;
import com.taehyung.mes.production.ProductionResult;
import com.taehyung.mes.quality.QualityInspection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DashboardMapper {
    DashboardSummary findSummary();
    List<DashboardMaterialLots> findMaterialLots();
    List<WorkOrder> findRecentOrders();
    List<ProductionResult> findRecentResults();
    List<QualityInspection> findRecentInspections();
}
