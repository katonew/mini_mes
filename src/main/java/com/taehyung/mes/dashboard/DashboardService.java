package com.taehyung.mes.dashboard;

import com.taehyung.mes.workorder.WorkOrder;
import com.taehyung.mes.production.ProductionResult;
import com.taehyung.mes.quality.QualityInspection;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private final DashboardMapper mapper;
    public DashboardService(DashboardMapper mapper) { this.mapper = mapper; }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public DashboardDetails get() {
        var summary = mapper.findSummary();
        if (summary == null) { throw new DataIntegrityViolationException("Dashboard aggregate row is missing."); }
        var warnings = new ArrayList<String>();
        if (summary.getWorkOrderCount() != summary.getReadyCount() + summary.getInProgressCount() + summary.getCompletedCount() + summary.getCancelledCount()) {
            warnings.add("전체 작업지시 건수와 상태별 합계가 다릅니다. 작업지시 상태를 확인해 주세요.");
        }
        if (summary.getProducedQty().compareTo(summary.getGoodQty().add(summary.getDefectQty())) != 0) {
            warnings.add("생산수량과 생산 양품·불량 합계가 일치하지 않습니다.");
        }
        if (summary.getInspectedQty().compareTo(summary.getAcceptedQty().add(summary.getRejectedQty())) != 0) {
            warnings.add("검사수량과 검사 합격·불합격 합계가 일치하지 않습니다.");
        }
        if (summary.getExcludedResultCount() > 0) {
            warnings.add("진행 중·완료 상태가 아닌 작업지시에 생산실적이 있습니다. 해당 실적은 생산 KPI와 최근 생산실적에서 제외했습니다.");
        }
        if (summary.getInvalidInspectionCount() > 0) {
            warnings.add("완료 상태가 아닌 작업지시에 검사 이력이 있습니다. 검사 KPI는 저장된 검사량을 표시하므로 상태 정합성을 확인해 주세요.");
        }
        if (summary.getInvalidTargetCount() > 0) {
            warnings.add("완료 작업지시 중 확정 검사 대상 누락 또는 대상 초과 검사 기록이 있습니다. 미검사 합계는 확정할 수 없습니다.");
        }
        return new DashboardDetails(summary, mapper.findMaterialLots(), mapper.findRecentOrders(), mapper.findRecentResults(),
                mapper.findRecentInspections(), warnings);
    }
    public record DashboardDetails(DashboardSummary summary, List<DashboardMaterialLots> materialLots,
                                   List<WorkOrder> recentOrders, List<ProductionResult> recentResults,
                                   List<QualityInspection> recentInspections, List<String> warnings) {}
}
