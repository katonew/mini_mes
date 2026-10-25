package com.taehyung.mes.item;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ItemMapper {
    List<Item> findAll(@Param("code") String code, @Param("name") String name);
    Item findById(@Param("itemId") Long itemId);
    Item findByIdForUpdate(@Param("itemId") Long itemId);
    boolean existsByCode(@Param("itemCode") String itemCode);
    int insert(Item item);
    int updateEditable(@Param("itemId") Long itemId,
                       @Param("itemName") String itemName, @Param("isActive") Integer isActive);
}
