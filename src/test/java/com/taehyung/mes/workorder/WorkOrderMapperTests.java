package com.taehyung.mes.workorder;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WorkOrderMapperTests {
    @Test void firstUseOnlyFillsNullAndNoHistoryOrCompletedMutationExists() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try (var source = getClass().getResourceAsStream("/mappers/workorder/WorkOrderMapper.xml")) {
            assertThat(source).isNotNull(); var document = factory.newDocumentBuilder().parse(source); var xpath = XPathFactory.newInstance().newXPath();
            assertThat(xpath.evaluate("/mapper/update[@id='markBomUsed']", document)).contains("first_used_at IS NULL");
            assertThat(xpath.evaluate("/mapper/update[@id='markRoutingUsed']", document)).contains("first_used_at IS NULL");
            assertThat((Double) xpath.evaluate("count(/mapper/delete)", document, XPathConstants.NUMBER)).isZero();
            assertThat((Double) xpath.evaluate("count(/mapper/insert)", document, XPathConstants.NUMBER)).isEqualTo(1.0);
            String updates = xpath.evaluate("/mapper/update[@id='start']", document) + xpath.evaluate("/mapper/update[@id='cancel']", document);
            assertThat(updates).contains("status='READY'").doesNotContain("COMPLETED", "bom_version_id", "routing_revision_id", "first_used_at");
            assertThat(xpath.evaluate("/mapper/select[@id='findTotals']", document)).contains("finished_goods_lot", "production_result").doesNotContain("production_result_correction");
        }
    }
}
