package com.taehyung.mes.production;

import com.taehyung.mes.item.Item;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductionPlanMapper {
    List<ProductionPlan> findAll(@Param("planId") Long planId, @Param("code") String code, @Param("name") String name);
    ProductionPlan findById(@Param("planId") Long planId);
    List<Item> findProducts();
    int insert(ProductionPlan plan);
}
