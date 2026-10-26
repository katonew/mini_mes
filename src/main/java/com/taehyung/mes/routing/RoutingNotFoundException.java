package com.taehyung.mes.routing;

public class RoutingNotFoundException extends RuntimeException {
    public RoutingNotFoundException() { super("요청한 Routing을 찾을 수 없습니다."); }
}
