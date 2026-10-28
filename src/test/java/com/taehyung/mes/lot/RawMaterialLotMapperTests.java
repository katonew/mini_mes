package com.taehyung.mes.lot;

import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import com.taehyung.mes.common.web.KstDateTimeFormatter;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RawMaterialLotMapperTests {
    @Test void insertOwnsUtcTimeAndInitialQuantityAndNoStockMutationSqlExists() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try (var source = getClass().getResourceAsStream("/mappers/lot/RawMaterialLotMapper.xml")) {
            assertThat(source).isNotNull(); var document = factory.newDocumentBuilder().parse(source); var xpath = XPathFactory.newInstance().newXPath();
            assertThat((Double) xpath.evaluate("count(/mapper/update | /mapper/delete)", document, XPathConstants.NUMBER)).isZero();
            String sql = xpath.evaluate("/mapper/insert[@id='insert']", document).replaceAll("\\s+", " ").trim();
            assertThat(sql).contains("#{registeredQty}, #{registeredQty}, UTC_TIMESTAMP(6), #{createdBy}")
                    .doesNotContain("#{registeredAt}", "#{createdAt}", "#{availableQty}");
            assertThat(xpath.evaluate("/mapper/select[@id='existsByLotNo']", document)).doesNotContain("item_id");
            String details = xpath.evaluate("/mapper/select[@id='findById']", document);
            assertThat(details).contains("SUM(c.consumed_qty)", "SUM(r.returned_qty)", "c.consumption_id=r.consumption_id");
        }
    }
    @Test void lotViewsUseTheExistingKstFormatterAndHaveNoManagedTimeInput() throws Exception {
        for (String view : new String[]{"list", "detail"}) {
            String html = resource("/templates/lot/raw/" + view + ".html");
            assertThat(html).contains("@kstDateTimeFormatter.formatUtc(lot.registeredAt", "(KST)").doesNotContain("(UTC)");
        }
        assertThat(resource("/templates/lot/raw/form.html")).doesNotContain("th:field=\"*{registeredAt}", "th:field=\"*{availableQty}");
        assertThat(new KstDateTimeFormatter().formatUtc(LocalDateTime.of(2026, 10, 26, 15, 30), "yyyy-MM-dd HH:mm"))
                .isEqualTo("2026-10-27 00:30");
    }
    private String resource(String path) throws Exception {
        try (var source = getClass().getResourceAsStream(path)) { assertThat(source).isNotNull(); return new String(source.readAllBytes(), StandardCharsets.UTF_8); }
    }
}
