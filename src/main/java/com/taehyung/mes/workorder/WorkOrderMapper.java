package com.taehyung.mes.workorder;

import com.taehyung.mes.production.ProductionPlan;
import com.taehyung.mes.production.ProductionResult;
import com.taehyung.mes.production.FinishedGoodsLot;
import java.math.BigDecimal;
import com.taehyung.mes.routing.RoutingRevision;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkOrderMapper {
    List<WorkOrder> findAll(@Param("id") Long id, @Param("planId") Long planId, @Param("keyword") String keyword, @Param("status") String status);
    WorkOrder findById(@Param("id") Long id);
    WorkOrder findByIdForUpdate(@Param("id") Long id);
    List<ProductionPlan> findPlans();
    ProductionPlan findPlan(@Param("planId") Long planId);
    ProductionPlan findPlanForUpdate(@Param("planId") Long planId);
    RoutingRevision findRevisionForUpdate(@Param("revisionId") Long revisionId);
    List<WorkOrderBomOption> findBomOptions(@Param("productId") Long productId);
    List<WorkOrderRoutingOption> findRoutingOptions(@Param("productId") Long productId);
    WorkOrderAllocation findAllocation(@Param("planId") Long planId);
    WorkOrderTotals findTotals(@Param("id") Long id);
    FinishedGoodsLot findFinishedLot(@Param("id") Long id);
    List<ProductionResult> findProductionResults(@Param("id") Long id);
    boolean hasExecutionHistory(@Param("id") Long id);
    int markBomUsed(@Param("versionId") Long versionId);
    int markRoutingUsed(@Param("revisionId") Long revisionId);
    int insert(WorkOrder order);
    int start(@Param("id") Long id, @Param("actor") String actor);
    int cancel(@Param("id") Long id, @Param("actor") String actor);
    int complete(@Param("id") Long id, @Param("targetQty") BigDecimal targetQty, @Param("actor") String actor);
}
