package com.taehyung.mes.lot;

import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** 실제 MyBatis 파싱과 동적 SQL 바인딩만 검사한다. DB에는 접속하지 않는다. */
class MaterialReturnMapperTests {
    @Test void sourceFilterBindsOnlyToTheOriginalConsumptionAndSearchParameters() throws Exception {
        var config=configuration(); var parameters=Map.of("workOrderId",1L,"consumptionId",7L,"lotNo","LOT","keyword","자재");
        var bound=config.getMappedStatement(MaterialReturnMapper.class.getName()+".findAll").getBoundSql(parameters);
        String sql=bound.getSql().replaceAll("\\s+"," ");
        assertThat(sql).contains("c.work_order_id=?","r.consumption_id=?","LOCATE(?,l.lot_no)","ORDER BY r.return_id DESC");
        assertThat(bound.getParameterMappings()).extracting(p -> p.getProperty()).containsExactly("workOrderId","consumptionId","lotNo","keyword","keyword");
        var empty=new java.util.HashMap<String,Object>(); empty.put("workOrderId",null); empty.put("consumptionId",null); empty.put("lotNo",""); empty.put("keyword","");
        assertThat(config.getMappedStatement(MaterialReturnMapper.class.getName()+".findAll").getBoundSql(empty).getParameterMappings()).isEmpty();
    }
    @Test void mapperStatementsParseAndInsertUsesOnlyReturnInputs() throws Exception {
        var config=configuration(); var row=new MaterialReturn(); row.setConsumptionId(7L); row.setReceiptId(11L); row.setReturnedQty(java.math.BigDecimal.ONE); row.setReason("사유"); row.setCreatedBy("DEV");
        var bound=config.getMappedStatement(MaterialReturnMapper.class.getName()+".insert").getBoundSql(row);
        assertThat(bound.getParameterMappings()).extracting(p -> p.getProperty()).containsExactly("consumptionId","receiptId","returnedQty","reason","createdBy");
        assertThat(bound.getSql()).contains("UTC_TIMESTAMP(6)");
        assertThat(config.getMappedStatement(MaterialReturnMapper.class.getName()+".findConsumptionForUpdate").getBoundSql(Map.of("consumptionId",7L)).getSql()).contains("FOR UPDATE").doesNotContain("JOIN");
    }
    private Configuration configuration() throws Exception {
        var config=new Configuration(); config.setMapUnderscoreToCamelCase(true);
        try (var source=getClass().getResourceAsStream("/mappers/lot/MaterialReturnMapper.xml")) {
            assertThat(source).isNotNull(); new XMLMapperBuilder(source,config,"mappers/lot/MaterialReturnMapper.xml",config.getSqlFragments()).parse();
        }
        return config;
    }
}
