package com.taehyung.mes.item;

import java.util.Arrays;
import java.util.List;

public enum ItemKind {
    RAW("원자재", true),
    SEMI("반제품", false),
    FINISHED("완제품", true);

    private final String label;
    private final boolean registrationAllowed;

    ItemKind(String label, boolean registrationAllowed) {
        this.label = label;
        this.registrationAllowed = registrationAllowed;
    }

    public String getCode() { return name(); }
    public String getLabel() { return label; }
    public boolean isRegistrationAllowed() { return registrationAllowed; }

    public static List<ItemKind> registrationOptions() {
        return Arrays.stream(values()).filter(ItemKind::isRegistrationAllowed).toList();
    }

    public static boolean canRegister(String code) {
        return registrationOptions().stream().anyMatch(kind -> kind.name().equals(code));
    }

    public static String labelOf(String code) {
        return Arrays.stream(values()).filter(kind -> kind.name().equals(code))
                .map(ItemKind::getLabel).findFirst().orElse(code);
    }
}
