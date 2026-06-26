package com.example.pstarchive.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeUtils {
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private TimeUtils() {
    }

    public static String nowIso() {
        return ISO_FORMATTER.format(Instant.now().atZone(ZoneId.systemDefault()));
    }
}
