package com.example.pstarchive.search;

import java.io.PrintStream;

public class SearchResultFormatter {
    private final PrintStream out;

    public SearchResultFormatter(PrintStream out) {
        this.out = out;
    }

    public void print(PathLabel store, SearchResponse response) {
        out.println("SEARCH START");
        out.println("store: " + store.value());
        out.println("query: " + response.query().original());
        out.println("normalizedQuery: " + response.query().comparable());
        out.println("limit: " + response.limit());
        out.println("context: " + response.contextChars());
        out.println();
        out.println("SEARCH SUMMARY");
        out.println("sqlCandidates: " + response.sqlCandidates());
        out.println("verifiedMessages: " + response.verifiedMessages().size());
        out.println("totalMatches: " + response.totalMatches());
        out.println();

        int index = 1;
        for (VerifiedMessage message : response.verifiedMessages()) {
            SearchCandidate candidate = message.candidate();
            out.println("[RESULT " + index++ + "]");
            out.println("messageId: " + candidate.id());
            out.println("descriptorNodeId: " + value(candidate.descriptorNodeId()));
            out.println("folder: " + value(candidate.folderPath()));
            out.println("subject: " + value(candidate.subject()));
            out.println("subjectStatus: " + value(candidate.subjectStatus()));
            out.println("senderName: " + value(candidate.senderName()));
            out.println("senderEmail: " + value(candidate.senderEmail()));
            out.println("sentAt: " + value(candidate.sentAt()));
            out.println("receivedAt: " + value(candidate.receivedAt()));
            out.println();
            out.println("MATCHES");
            for (FieldMatch match : message.matches()) {
                out.println("- field: " + match.field());
                out.println("  offset: " + match.offset());
                out.println("  length: " + match.length());
                out.println("  line: " + match.lineNumber());
                out.println("  paragraph: " + match.paragraphNumber());
                out.println("  policy: " + match.matchPolicy());
                out.println("  matchedText: " + value(match.matchedText()));
                out.println("  context: " + value(match.context()));
                out.println();
            }
            out.println("--------------------------------------------------");
        }
    }

    private String value(Object value) {
        return value == null ? "<null>" : String.valueOf(value);
    }

    public record PathLabel(String value) {
    }
}
