package com.taehyung.mes.production;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProductionPlanMapperTests {
    @Test
    void onlyPlanInsertIsAvailableAndWorkOrderReferenceIsReadOnly() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try (var source = getClass().getResourceAsStream("/mappers/production/ProductionPlanMapper.xml")) {
            assertThat(source).isNotNull(); var document = factory.newDocumentBuilder().parse(source); var xpath = XPathFactory.newInstance().newXPath();
            assertThat((Double) xpath.evaluate("count(/mapper/update | /mapper/delete)", document, XPathConstants.NUMBER)).isZero();
            String sql = xpath.evaluate("/mapper/insert[@id='insert']", document).replaceAll("\\s+", " ").trim();
            assertThat(sql).isEqualTo("INSERT INTO production_plan (product_item_id, planned_qty, planned_date, created_by) "
                    + "VALUES (#{productItemId}, #{plannedQty}, #{plannedDate}, #{createdBy})");
            String query = xpath.evaluate("/mapper/sql[@id='columns']", document);
            assertThat(query).contains("FROM work_order w WHERE w.production_plan_id = p.production_plan_id");
        }
    }
}
