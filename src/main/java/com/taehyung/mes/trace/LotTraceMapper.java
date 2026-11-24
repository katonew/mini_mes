package com.taehyung.mes.trace;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LotTraceMapper {
    List<ForwardLotTraceOrder> findForward(@Param("rawLotId") Long rawLotId);
}
