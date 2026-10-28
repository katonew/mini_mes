package com.taehyung.mes.lot;

public class RawMaterialLotNotFoundException extends RuntimeException {
    public RawMaterialLotNotFoundException() { super("요청한 원자재 LOT을 찾을 수 없습니다."); }
}
