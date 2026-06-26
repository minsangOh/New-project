package com.example.pstarchive.pst;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class SafePstFieldExtractor {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private long fieldErrors;

    public String stringValue(ThrowingSupplier<?> supplier) {
        try {
            Object value = supplier.get();
            if (value == null) {
                return "<null>";
            }
            String text = String.valueOf(value);
            return text.isEmpty() ? "<empty>" : text;
        } catch (Exception e) {
            fieldErrors++;
            return "<error: " + e.getClass().getSimpleName() + ": " + safeMessage(e) + ">";
        }
    }

    public String dateValue(ThrowingSupplier<Date> supplier) {
        try {
            Date value = supplier.get();
            if (value == null) {
                return "<null>";
            }
            return DATE_FORMATTER.format(value.toInstant().atZone(ZoneId.systemDefault()));
        } catch (Exception e) {
            fieldErrors++;
            return "<error: " + e.getClass().getSimpleName() + ": " + safeMessage(e) + ">";
        }
    }

    public long fieldErrors() {
        return fieldErrors;
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "no message";
        }
        return message.replace("\r", " ").replace("\n", " ");
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
