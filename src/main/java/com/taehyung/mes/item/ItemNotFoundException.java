package com.taehyung.mes.item;

public class ItemNotFoundException extends RuntimeException {
    public ItemNotFoundException() {
        super("요청한 품목을 찾을 수 없습니다.");
    }
}
