package com.example.pstarchive.inspect;

public record MessageDetail(
        long id,
        Long folderId,
        String folderPath,
        Long descriptorNodeId,
        String internetMessageId,
        String subject,
        String subjectStatus,
        String subjectSource,
        String senderName,
        String senderEmail,
        String recipients,
        String cc,
        String sentAt,
        String receivedAt,
        String bodyText,
        String bodyTextStatus,
        String bodyTextSource,
        String bodyHtml,
        String bodyHtmlStatus,
        String bodyHtmlSource,
        String bodyHtmlText,
        String bodyHtmlTextStatus,
        Integer bodyTextLength,
        Integer bodyHtmlLength,
        Integer bodyHtmlTextLength,
        Long indexedAt,
        String parseStatus
) {
}
