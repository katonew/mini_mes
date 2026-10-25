package com.taehyung.mes.item;

import java.util.Arrays;

public enum ItemUnit {
    EA("개"), M("미터"), G("그램");

    private final String label;

    ItemUnit(String label) { this.label = label; }
    public String getCode() { return name(); }
    public String getLabel() { return label; }

    public static boolean isSupported(String code) {
        return Arrays.stream(values()).anyMatch(unit -> unit.name().equals(code));
    }

    public static String labelOf(String code) {
        return Arrays.stream(values()).filter(unit -> unit.name().equals(code))
                .map(ItemUnit::getLabel).findFirst().orElse(code);
    }
}
