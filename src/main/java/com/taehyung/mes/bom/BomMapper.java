package com.taehyung.mes.bom;

import com.taehyung.mes.item.Item;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BomMapper {
    List<BomVersion> findAll(@Param("code") String code, @Param("name") String name);
    Bom findBom(@Param("bomId") Long bomId);
    Bom findBomForUpdate(@Param("bomId") Long bomId);
    BomVersion findVersion(@Param("versionId") Long versionId);
    BomVersion findVersionForUpdate(@Param("versionId") Long versionId);
    List<BomComponent> findComponents(@Param("versionId") Long versionId);
    List<Item> findSelectableItems(@Param("kind") String kind, @Param("unit") String unit);
    List<Item> findItemsByIds(@Param("ids") List<Long> ids);
    boolean existsByCode(@Param("code") String code);
    boolean existsVersion(@Param("bomId") Long bomId, @Param("versionNo") Long versionNo);
    boolean hasWorkOrders(@Param("versionId") Long versionId);
    int insertBom(Bom bom);
    int insertVersion(BomVersion version);
    int insertComponent(BomComponent component);
    int updateQuantity(@Param("versionId") Long versionId, @Param("componentId") Long componentId,
                       @Param("quantity") BigDecimal quantity);
}
