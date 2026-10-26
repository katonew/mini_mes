package com.taehyung.mes.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class KstDateTimeFormatterTests {
    private final KstDateTimeFormatter formatter = new KstDateTimeFormatter();

    @Test
    void formatsUtcAcrossTheKoreanDateBoundary() {
        LocalDateTime utc = LocalDateTime.of(2026, 10, 26, 15, 30);

        assertEquals("2026-10-27 00:30", formatter.formatUtc(utc, "yyyy-MM-dd HH:mm"));
    }
}
