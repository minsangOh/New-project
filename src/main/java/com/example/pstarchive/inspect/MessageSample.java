package com.example.pstarchive.inspect;

public record MessageSample(
        long id,
        String folderPath,
        Long descriptorNodeId,
        String subject,
        String subjectStatus,
        String senderName,
        String senderEmail,
        String recipients,
        String cc,
        String sentAt,
        String receivedAt,
        Integer bodyTextLength,
        Integer bodyHtmlLength,
        Integer bodyHtmlTextLength,
        String bodyTextStatus,
        String bodyHtmlStatus,
        String bodyHtmlTextStatus,
        String bodyText,
        String bodyHtmlText
) {
}
