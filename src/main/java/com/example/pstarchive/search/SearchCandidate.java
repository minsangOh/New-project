package com.example.pstarchive.search;

import java.util.Map;

public record SearchCandidate(
        long id,
        String folderPath,
        Long descriptorNodeId,
        String internetMessageId,
        String subject,
        String subjectStatus,
        String senderName,
        String senderEmail,
        String recipients,
        String cc,
        String sentAt,
        String receivedAt,
        String bodyText,
        String bodyTextStatus,
        String bodyHtmlText,
        String bodyHtmlTextStatus
) {
    public String fieldValue(SearchField field) {
        return switch (field) {
            case SUBJECT -> subject;
            case SENDER_NAME -> senderName;
            case SENDER_EMAIL -> senderEmail;
            case RECIPIENTS -> recipients;
            case CC -> cc;
            case FOLDER_PATH -> folderPath;
            case BODY_TEXT -> bodyText;
            case BODY_HTML_TEXT -> bodyHtmlText;
        };
    }

    public Map<String, String> statusByField() {
        return Map.of(
                "subject", value(subjectStatus),
                "body_text", value(bodyTextStatus),
                "body_html_text", value(bodyHtmlTextStatus)
        );
    }

    private String value(String value) {
        return value == null ? "<null>" : value;
    }
}
