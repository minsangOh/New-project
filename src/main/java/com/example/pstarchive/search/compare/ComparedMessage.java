package com.example.pstarchive.search.compare;

import java.util.List;

public record ComparedMessage(
        long messageId,
        String subject,
        String senderName,
        String receivedAt,
        List<String> matchedFields,
        List<String> matchPolicies,
        long visibleMatchCount,
        long hiddenBrokenMatches,
        String preview
) {
}
