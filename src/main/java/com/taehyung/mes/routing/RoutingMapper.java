package com.taehyung.mes.routing;

import com.taehyung.mes.item.Item;
import com.taehyung.mes.process.ManufacturingProcess;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoutingMapper {
    List<RoutingRevision> findAll(@Param("code") String code, @Param("name") String name);
    Routing findRouting(@Param("routingId") Long routingId);
    Routing findRoutingForUpdate(@Param("routingId") Long routingId);
    RoutingRevision findRevision(@Param("revisionId") Long revisionId);
    List<RoutingStep> findSteps(@Param("revisionId") Long revisionId);
    List<Item> findProducts();
    List<ManufacturingProcess> findProcesses();
    List<ManufacturingProcess> findProcessesByIds(@Param("ids") List<Long> ids);
    boolean existsByCode(@Param("code") String code);
    boolean existsRevision(@Param("routingId") Long routingId, @Param("revisionNo") Long revisionNo);
    int insertRouting(Routing routing);
    int insertRevision(RoutingRevision revision);
    int insertStep(RoutingStep step);
}
