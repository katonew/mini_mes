package com.taehyung.mes.process;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProcessMapper {
    List<ManufacturingProcess> findAll(@Param("code") String code, @Param("name") String name);
    ManufacturingProcess findById(@Param("processId") Long processId);
    ManufacturingProcess findByIdForUpdate(@Param("processId") Long processId);
    boolean existsByCode(@Param("processCode") String processCode);
    int insert(ManufacturingProcess process);
    int updateEditable(@Param("processId") Long processId, @Param("processName") String processName,
                       @Param("description") String description, @Param("isActive") Integer isActive);
}
