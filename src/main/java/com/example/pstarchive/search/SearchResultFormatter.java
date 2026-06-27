package com.example.pstarchive.search;

import com.example.pstarchive.textquality.TextQualityAnalyzer;
import com.example.pstarchive.textquality.TextQualityResult;

import java.io.PrintStream;
import java.util.Arrays;

public class SearchResultFormatter {
    private final PrintStream out;
    private final TextQualityAnalyzer qualityAnalyzer = new TextQualityAnalyzer();

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
                SearchField field = fieldFor(match.field());
                TextQualityResult quality = qualityAnalyzer.diagnose(field == null ? null : candidate.fieldValue(field));
                out.println("- field: " + match.field());
                out.println("  fieldStatus: " + fieldStatus(candidate, field));
                out.println("  textQuality: " + quality.level());
                out.println("  qualityWarnings: " + (quality.warnings().isEmpty() ? "<none>" : String.join(",", quality.warnings())));
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

    private SearchField fieldFor(String fieldName) {
        return Arrays.stream(SearchField.values())
                .filter(field -> field.columnName().equalsIgnoreCase(fieldName) || field.displayName().equalsIgnoreCase(fieldName))
                .findFirst()
                .orElse(null);
    }

    private String fieldStatus(SearchCandidate candidate, SearchField field) {
        if (field == null) {
            return "<unknown>";
        }
        return candidate.statusByField().getOrDefault(field.columnName(), "<not_tracked>");
    }

    private String value(Object value) {
        return value == null ? "<null>" : String.valueOf(value);
    }

    public record PathLabel(String value) {
    }
}
