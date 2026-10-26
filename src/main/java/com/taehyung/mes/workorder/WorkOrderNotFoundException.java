package com.taehyung.mes.workorder;

public class WorkOrderNotFoundException extends RuntimeException {
    public WorkOrderNotFoundException() { super("요청한 작업지시를 찾을 수 없습니다."); }
}
