package com.example.pstarchive.search;

import com.example.pstarchive.textquality.TextQualityAnalyzer;
import com.example.pstarchive.textquality.TextQualityLevel;
import com.example.pstarchive.textquality.TextQualityResult;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SearchResultFormatter {
    private final PrintStream out;
    private final TextQualityAnalyzer qualityAnalyzer = new TextQualityAnalyzer();

    public SearchResultFormatter(PrintStream out) {
        this.out = out;
    }

    public void print(PathLabel store, SearchResponse response) {
        print(store, response, false);
    }

    public void print(PathLabel store, SearchResponse response, boolean includeBroken) {
        List<ResultView> resultViews = resultViews(response, includeBroken);
        long hiddenBrokenMatches = resultViews.stream().mapToLong(ResultView::hiddenBrokenMatches).sum();
        long displayedMatches = resultViews.stream().mapToLong(view -> view.visibleMatches().size()).sum();
        long displayedMessages = resultViews.stream().filter(view -> !view.visibleMatches().isEmpty()).count();

        out.println("SEARCH START");
        out.println("store: " + store.value());
        out.println("query: " + response.query().original());
        out.println("normalizedQuery: " + response.query().comparable());
        out.println("limit: " + response.limit());
        out.println("context: " + response.contextChars());
        out.println("includeBroken: " + includeBroken);
        out.println();
        out.println("SEARCH SUMMARY");
        out.println("sqlCandidates: " + response.sqlCandidates());
        out.println("verifiedMessages: " + response.verifiedMessages().size());
        out.println("displayedMessages: " + displayedMessages);
        out.println("totalMatches: " + response.totalMatches());
        out.println("displayedMatches: " + displayedMatches);
        out.println("hiddenBrokenMatches: " + hiddenBrokenMatches);
        out.println();

        int index = 1;
        for (ResultView view : resultViews) {
            if (view.visibleMatches().isEmpty()) {
                continue;
            }
            SearchCandidate candidate = view.message().candidate();
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
            out.println("hiddenBrokenMatches: " + view.hiddenBrokenMatches());
            out.println();
            out.println("MATCHES");
            for (FieldMatch match : view.visibleMatches()) {
                printMatch(candidate, match);
            }
            out.println("--------------------------------------------------");
        }
    }

    private void printMatch(SearchCandidate candidate, FieldMatch match) {
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

    private List<ResultView> resultViews(SearchResponse response, boolean includeBroken) {
        List<ResultView> views = new ArrayList<>();
        for (VerifiedMessage message : response.verifiedMessages()) {
            List<FieldMatch> visible = new ArrayList<>();
            long hidden = 0;
            for (FieldMatch match : message.matches()) {
                if (!includeBroken && isBroken(message.candidate(), match)) {
                    hidden++;
                } else {
                    visible.add(match);
                }
            }
            views.add(new ResultView(message, List.copyOf(visible), hidden));
        }
        return views;
    }

    private boolean isBroken(SearchCandidate candidate, FieldMatch match) {
        SearchField field = fieldFor(match.field());
        return qualityAnalyzer.diagnose(field == null ? null : candidate.fieldValue(field)).level() == TextQualityLevel.BROKEN;
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

    private record ResultView(VerifiedMessage message, List<FieldMatch> visibleMatches, long hiddenBrokenMatches) {
    }

    public record PathLabel(String value) {
    }
}
