package com.example.pstarchive.pst;

public record PstMailPreview(
        String folderPath,
        String descriptorNodeId,
        String internetMessageId,
        String subject,
        String senderName,
        String senderEmail,
        String to,
        String cc,
        String sentAt,
        String receivedAt,
        String plainBodyPreview,
        String htmlBodyPreview,
        String htmlTextPreview
) {
}
