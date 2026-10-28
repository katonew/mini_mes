package com.taehyung.mes.lot;

import com.taehyung.mes.item.Item;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RawMaterialLotMapper {
    List<RawMaterialLot> findAll(@Param("lotNo") String lotNo, @Param("code") String code, @Param("name") String name);
    RawMaterialLot findById(@Param("lotId") Long lotId);
    List<Item> findActiveRawItems();
    boolean existsByLotNo(@Param("lotNo") String lotNo);
    int insert(RawMaterialLot lot);
}
