package com.taehyung.mes.trace;

import com.taehyung.mes.lot.RawMaterialLot;
import java.util.List;

public class ForwardLotTraceDetails {
    private final RawMaterialLot lot;
    private final List<ForwardLotTraceOrder> orders;
    public ForwardLotTraceDetails(RawMaterialLot lot, List<ForwardLotTraceOrder> orders) {
        this.lot = lot; this.orders = orders;
    }
    public RawMaterialLot getLot() { return lot; }
    public List<ForwardLotTraceOrder> getOrders() { return orders; }
}
