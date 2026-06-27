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
    public String visibilityClass() {
        if (visibleMatchCount > 0) {
            return "visible";
        }
        if (hiddenBrokenMatches > 0) {
            return "hidden_only";
        }
        return "none";
    }
}
