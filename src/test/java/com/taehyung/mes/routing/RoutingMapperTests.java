package com.taehyung.mes.routing;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RoutingMapperTests {
    @Test
    void savedRevisionAndSnapshotHaveNoUpdateOrDeleteSql() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try (var source = getClass().getResourceAsStream("/mappers/routing/RoutingMapper.xml")) {
            assertThat(source).isNotNull();
            var document = factory.newDocumentBuilder().parse(source);
            var xpath = XPathFactory.newInstance().newXPath();
            assertThat((Double) xpath.evaluate("count(/mapper/update | /mapper/delete)", document, XPathConstants.NUMBER)).isZero();
            String insert = xpath.evaluate("/mapper/insert[@id='insertStep']", document);
            assertThat(insert).contains("process_name_snapshot", "#{processNameSnapshot}", "#{stepNo}");
            String history = xpath.evaluate("/mapper/select[@id='findSteps']", document);
            assertThat(history).contains("s.process_name_snapshot", "ORDER BY s.step_no");
        }
    }
}
