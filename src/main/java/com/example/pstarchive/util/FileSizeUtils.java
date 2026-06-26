package com.example.pstarchive.util;

import java.util.Locale;

public final class FileSizeUtils {
    private static final long KB = 1024L;
    private static final long MB = KB * 1024L;
    private static final long GB = MB * 1024L;

    private FileSizeUtils() {
    }

    public static String humanReadable(long bytes) {
        if (bytes >= GB) {
            return String.format(Locale.ROOT, "%.2f GB", bytes / (double) GB);
        }
        if (bytes >= MB) {
            return String.format(Locale.ROOT, "%.2f MB", bytes / (double) MB);
        }
        if (bytes >= KB) {
            return String.format(Locale.ROOT, "%.2f KB", bytes / (double) KB);
        }
        return bytes + " B";
    }
}
