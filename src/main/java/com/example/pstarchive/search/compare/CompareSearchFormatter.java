package com.example.pstarchive.search.compare;

import java.io.PrintStream;

public class CompareSearchFormatter {
    private final PrintStream out;

    public CompareSearchFormatter(PrintStream out) {
        this.out = out;
    }

    public void print(CompareSearchReport report) {
        out.println("COMPARE SEARCH ENGINES");
        out.println("store: " + report.storePath());
        out.println("query: " + report.query());
        out.println("field: " + report.field());
        out.println("limit: " + report.limit());
        out.println("includeBroken: " + report.includeBroken());
        out.println();
        out.println("SUMMARY");
        out.println("likeCandidates: " + report.likeCandidates());
        out.println("fts5Candidates: " + report.fts5Candidates());
        out.println("likeVerifiedMessages: " + report.likeVerifiedMessages());
        out.println("fts5VerifiedMessages: " + report.fts5VerifiedMessages());
        out.println("commonVerifiedMessages: " + report.commonVerifiedMessages());
        out.println("likeDisplayedMessages: " + report.likeDisplayedMessages());
        out.println("fts5DisplayedMessages: " + report.fts5DisplayedMessages());
        out.println("commonDisplayedMessages: " + report.commonDisplayedMessages());
        out.println("likeOnlyVerifiedMessages: " + report.likeOnlyVerifiedMessages());
        out.println("fts5OnlyVerifiedMessages: " + report.fts5OnlyVerifiedMessages());
        out.println("likeOnlyDisplayedMessages: " + report.likeOnlyDisplayedMessages());
        out.println("fts5OnlyDisplayedMessages: " + report.fts5OnlyDisplayedMessages());
        out.println("likeOnlyHiddenOnlyMessages: " + report.likeOnlyHiddenOnlyMessages());
        out.println("fts5OnlyHiddenOnlyMessages: " + report.fts5OnlyHiddenOnlyMessages());
        if (report.hasFts5Error()) {
            out.println("fts5Error: " + report.fts5Error());
        }
        out.println();
        printMessages("LIKE ONLY VERIFIED MESSAGES", report.likeOnlyMessages());
        out.println();
        printMessages("FTS5 ONLY VERIFIED MESSAGES", report.fts5OnlyMessages());
        out.println();
        printDiagnosisHint(report);
    }

    private void printMessages(String title, java.util.List<ComparedMessage> messages) {
        out.println(title);
        if (messages.isEmpty()) {
            out.println("<none>");
            return;
        }
        int index = 1;
        for (ComparedMessage message : messages) {
            out.println("[" + index++ + "]");
            out.println("messageId: " + message.messageId());
            out.println("subject: " + value(message.subject()));
            out.println("senderName: " + value(message.senderName()));
            out.println("receivedAt: " + value(message.receivedAt()));
            out.println("matchedFields: " + list(message.matchedFields()));
            out.println("matchPolicies: " + list(message.matchPolicies()));
            out.println("visibleMatchCount: " + message.visibleMatchCount());
            out.println("hiddenBrokenMatches: " + message.hiddenBrokenMatches());
            out.println("visibilityClass: " + message.visibilityClass());
            out.println("preview: " + value(message.preview()));
            out.println();
        }
    }

    private void printDiagnosisHint(CompareSearchReport report) {
        out.println("DIAGNOSIS HINT");
        if (report.hasFts5Error()) {
            out.println("FTS5 comparison failed. Build or verify the FTS5 index before comparing engines.");
        } else if (report.likeOnlyDisplayedMessages() > 0) {
            out.println("FTS5 missed visible verified messages. Do not use FTS5 as default.");
            out.println("Review LIKE-only visible fields before designing hybrid fallback.");
        } else if (report.fts5OnlyDisplayedMessages() > 0) {
            out.println("FTS5 found verified messages not present in LIKE results. Review limits and ordering before changing policy.");
        } else if (report.likeOnlyHiddenOnlyMessages() > 0 || report.fts5OnlyHiddenOnlyMessages() > 0) {
            out.println("FTS5 differs only on hidden BROKEN matches under current display policy.");
        } else {
            out.println("LIKE and FTS5 verified message sets match for this query and limit.");
        }
    }

    private String list(java.util.List<String> values) {
        return values == null || values.isEmpty() ? "<none>" : String.join(", ", values);
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "<null>" : value;
    }
}
