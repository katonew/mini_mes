package com.taehyung.mes.production;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProductionResultMapperTests {
    @Test void mapperUsesTheExistingReceiptAndUtcBusinessTimeWithoutEditingHistory() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mappers/production/ProductionResultMapper.xml"));
        String schema = Files.readString(Path.of("db/schema.sql"));
        assertThat(xml).contains("'PRODUCTION_RESULT'", "UTC_TIMESTAMP(6)", "INSERT INTO request_receipt", "INSERT INTO production_result", "INSERT INTO finished_goods_lot")
                .contains("INSERT INTO production_result_correction", "AND revision_no=#{command.expectedRevision}")
                .doesNotContain("DELETE FROM production_result", "UPDATE production_result_correction");
        assertThat(schema).contains("UNIQUE KEY uq_fg_work_order", "UNIQUE KEY uq_fg_lot_no", "UNIQUE KEY uq_request_key", "UNIQUE KEY uq_result_receipt");
    }
}
