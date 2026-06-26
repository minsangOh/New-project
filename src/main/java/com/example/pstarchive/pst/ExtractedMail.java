package com.example.pstarchive.pst;

import com.example.pstarchive.encoding.ExtractedText;
import com.example.pstarchive.index.IndexFieldError;

import java.util.List;

public record ExtractedMail(
        Long folderId,
        String folderPath,
        Long descriptorNodeId,
        String internetMessageId,
        ExtractedText subject,
        ExtractedText senderName,
        String senderEmail,
        ExtractedText recipients,
        ExtractedText cc,
        String sentAt,
        String receivedAt,
        ExtractedText bodyText,
        ExtractedText bodyHtml,
        ExtractedText bodyHtmlText,
        String parseStatus,
        List<IndexFieldError> errors
) {
}
