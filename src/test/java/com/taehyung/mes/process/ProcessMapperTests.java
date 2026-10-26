package com.taehyung.mes.process;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** DB 비연결 SQL 계약 검증. 실제 FK·스냅샷 데이터 보존 실행 검증은 아니다. */
class ProcessMapperTests {
    @Test
    void updateCannotChangeRoutingSnapshotsIdentityOrCreationMetadata() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try (var source = getClass().getResourceAsStream("/mappers/process/ProcessMapper.xml")) {
            assertThat(source).isNotNull();
            var document = factory.newDocumentBuilder().parse(source);
            var xpath = XPathFactory.newInstance().newXPath();
            String sql = xpath.evaluate("/mapper/update[@id='updateEditable']", document).replaceAll("\\s+", " ").trim();
            assertThat(sql).isEqualTo("UPDATE manufacturing_process SET process_name = #{processName}, description = #{description}, "
                    + "is_active = #{isActive}, updated_at = UTC_TIMESTAMP(6) WHERE process_id = #{processId}");
            assertThat((Double) xpath.evaluate("count(/mapper/delete)", document, XPathConstants.NUMBER)).isZero();
            assertThat(document.getDocumentElement().getTextContent()).doesNotContain("routing_step", "process_name_snapshot");
        }
    }
}
