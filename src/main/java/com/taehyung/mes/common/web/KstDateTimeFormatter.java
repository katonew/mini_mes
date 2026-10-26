package com.taehyung.mes.common.web;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component("kstDateTimeFormatter")
public class KstDateTimeFormatter {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public String formatUtc(LocalDateTime utcDateTime, String pattern) {
        return utcDateTime.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(SEOUL)
                .format(DateTimeFormatter.ofPattern(pattern));
    }
}
