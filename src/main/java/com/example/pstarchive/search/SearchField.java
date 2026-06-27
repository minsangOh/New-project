package com.example.pstarchive.search;

import java.util.Arrays;
import java.util.List;

public enum SearchField {
    SUBJECT("subject", "subject"),
    SENDER_NAME("sender_name", "senderName"),
    SENDER_EMAIL("sender_email", "senderEmail"),
    RECIPIENTS("recipients", "recipients"),
    CC("cc", "cc"),
    FOLDER_PATH("folder_path", "folder"),
    BODY_TEXT("body_text", "body_text"),
    BODY_HTML_TEXT("body_html_text", "body_html_text");

    private final String columnName;
    private final String displayName;

    SearchField(String columnName, String displayName) {
        this.columnName = columnName;
        this.displayName = displayName;
    }

    public String columnName() {
        return columnName;
    }

    public String displayName() {
        return displayName;
    }

    public static List<SearchField> allSearchable() {
        return List.of(SUBJECT, SENDER_NAME, SENDER_EMAIL, RECIPIENTS, CC, FOLDER_PATH, BODY_TEXT, BODY_HTML_TEXT);
    }

    public static List<SearchField> fromOption(String option) {
        if (option == null || option.isBlank() || "all".equalsIgnoreCase(option)) {
            return allSearchable();
        }
        return switch (option.toLowerCase(java.util.Locale.ROOT)) {
            case "subject" -> List.of(SUBJECT);
            case "sender" -> List.of(SENDER_NAME, SENDER_EMAIL);
            case "recipients" -> List.of(RECIPIENTS);
            case "cc" -> List.of(CC);
            case "folder" -> List.of(FOLDER_PATH);
            case "body" -> List.of(BODY_TEXT, BODY_HTML_TEXT);
            default -> Arrays.stream(values())
                    .filter(field -> field.columnName.equalsIgnoreCase(option) || field.displayName.equalsIgnoreCase(option))
                    .findFirst()
                    .map(List::of)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown search field: " + option));
        };
    }
}
