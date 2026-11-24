package com.taehyung.mes.trace;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LotTraceMapperTests {
    @Test void forwardQueryKeepsIndependentAggregatesAndReturnedHistory() throws Exception {
        try (var source = getClass().getResourceAsStream("/mappers/trace/LotTraceMapper.xml")) {
            assertThat(source).as("정방향 전용 Mapper").isNotNull();
            String sql = new String(source.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains("GROUP BY r.consumption_id", "GROUP BY c.work_order_id",
                    "GROUP BY p.finished_lot_id", "GROUP BY q.finished_lot_id", "LEFT JOIN finished_goods_lot");
            assertThat(sql).doesNotContain("production_result_correction", "<insert", "<update", "<delete", "HAVING");
        }
    }
}
