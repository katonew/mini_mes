package com.taehyung.mes.lot;

import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MaterialConsumptionMapperTests {
    @Test void stockReceiptAndConsumptionSqlRespectTheExistingSchemaAndNoExcludedWritesExist() throws Exception {
        var factory = DocumentBuilderFactory.newInstance(); factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try (var source = getClass().getResourceAsStream("/mappers/lot/MaterialConsumptionMapper.xml")) {
            var document = factory.newDocumentBuilder().parse(source); var xpath = XPathFactory.newInstance().newXPath();
            assertThat((Double) xpath.evaluate("count(/mapper/delete)", document, XPathConstants.NUMBER)).isZero();
            assertThat((Double) xpath.evaluate("count(/mapper/insert)", document, XPathConstants.NUMBER)).isEqualTo(2d);
            assertThat((Double) xpath.evaluate("count(/mapper/update)", document, XPathConstants.NUMBER)).isEqualTo(2d);
            String stock = xpath.evaluate("/mapper/update[@id='decreaseAvailable']", document);
            assertThat(stock).contains("available_qty=available_qty-#{quantity}", "available_qty >= #{quantity}", "#{quantity} > 0");
            assertThat(xpath.evaluate("/mapper/select[@id='findLotForUpdate']", document)).contains("FOR UPDATE").doesNotContain("JOIN item");
            assertThat(xpath.evaluate("/mapper/insert[@id='insert']", document)).contains("UTC_TIMESTAMP(6)").doesNotContain("#{consumedAt}");
            assertThat(xpath.evaluate("/mapper/insert[@id='claimReceipt']", document)).contains("'MATERIAL_CONSUME'");
            assertThat(xpath.evaluate("/mapper/update[@id='finishReceipt']", document)).contains("processed_at IS NULL", "initial_outcome_snapshot IS NULL");
            assertThat(xpath.evaluate("/mapper/select[@id='findMaterials']", document)).contains("v.bom_version_id=w.bom_version_id", "SUM(r.returned_qty)");
        }
    }
    @Test void viewsUseKstAndKeepRequestKeyAcrossBothForms() throws Exception {
        String form = resource("/templates/lot/consumption/form.html");
        assertThat(form).contains("name=\"requestKey\"", "th:field=\"*{requestKey}\"", "material.expectedQtyDisplay", "material.netConsumedQty")
                .doesNotContain("th:field=\"*{consumedAt}\"", "th:field=\"*{createdBy}\"");
        assertThat(resource("/templates/lot/consumption/detail.html")).contains("@kstDateTimeFormatter.formatUtc(consumption.outcome.consumedAt", "consumption.outcome.availableBefore", "consumption.outcome.availableAfter");
    }
    private String resource(String path) throws Exception {
        try (var source = getClass().getResourceAsStream(path)) { assertThat(source).isNotNull(); return new String(source.readAllBytes(), StandardCharsets.UTF_8); }
    }
}
