package com.taehyung.mes.workorder;

import java.util.Arrays;

public enum WorkOrderStatus {
    READY("대기"), IN_PROGRESS("진행 중"), COMPLETED("완료"), CANCELLED("취소");
    private final String label;
    WorkOrderStatus(String label) { this.label = label; }
    public String getCode() { return name(); }
    public String getLabel() { return label; }
    public static boolean supports(String code) { return Arrays.stream(values()).anyMatch(state -> state.name().equals(code)); }
    public static String labelOf(String code) { return Arrays.stream(values()).filter(state -> state.name().equals(code)).map(WorkOrderStatus::getLabel).findFirst().orElse(code); }
}
