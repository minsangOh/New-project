package com.example.pstarchive.search.compare;

import com.example.pstarchive.search.FieldMatch;
import com.example.pstarchive.search.RawFieldVerifier;
import com.example.pstarchive.search.SearchCandidate;
import com.example.pstarchive.search.SearchEngineType;
import com.example.pstarchive.search.SearchField;
import com.example.pstarchive.search.SearchQueryNormalizer;
import com.example.pstarchive.search.SearchResponse;
import com.example.pstarchive.search.SearchStoreService;
import com.example.pstarchive.search.VerifiedMessage;
import com.example.pstarchive.textquality.TextQualityAnalyzer;
import com.example.pstarchive.textquality.TextQualityLevel;
import com.example.pstarchive.util.TextPreviewer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CompareSearchService {
    private static final int COMPARE_CONTEXT_CHARS = 120;
    private static final int COMPARE_MAX_MATCHES_PER_MESSAGE = 10;
    private static final int PREVIEW_CHARS = 120;

    private final TextQualityAnalyzer qualityAnalyzer = new TextQualityAnalyzer();

    public CompareSearchReport compare(Path storePath, String query, String fieldOption, int limit, boolean includeBroken) throws Exception {
        int safeLimit = Math.max(1, limit);
        List<SearchField> fields = SearchField.fromOption(fieldOption);
        SearchResponse like = search(storePath, query, safeLimit, fields, SearchEngineType.LIKE);
        SearchResponse fts5 = null;
        String fts5Error = null;
        try {
            fts5 = search(storePath, query, safeLimit, fields, SearchEngineType.FTS5);
        } catch (Exception e) {
            fts5Error = safeMessage(e);
        }

        Map<Long, VerifiedMessage> likeMessages = byMessageId(like);
        Map<Long, VerifiedMessage> fts5Messages = fts5 == null ? Map.of() : byMessageId(fts5);
        Set<Long> common = new LinkedHashSet<>(likeMessages.keySet());
        common.retainAll(fts5Messages.keySet());

        return new CompareSearchReport(
                storePath.toAbsolutePath().normalize(),
                query,
                fieldOption == null || fieldOption.isBlank() ? "all" : fieldOption,
                safeLimit,
                includeBroken,
                like.sqlCandidates(),
                fts5 == null ? 0 : fts5.sqlCandidates(),
                like.verifiedMessages().size(),
                fts5 == null ? 0 : fts5.verifiedMessages().size(),
                common.size(),
                difference(likeMessages, fts5Messages, includeBroken),
                difference(fts5Messages, likeMessages, includeBroken),
                fts5Error
        );
    }

    private SearchResponse search(Path storePath, String query, int limit, List<SearchField> fields, SearchEngineType engine) throws Exception {
        return new SearchStoreService(new SearchQueryNormalizer(), engine.create(), new RawFieldVerifier())
                .search(storePath, query, limit, COMPARE_CONTEXT_CHARS, COMPARE_MAX_MATCHES_PER_MESSAGE, fields);
    }

    private Map<Long, VerifiedMessage> byMessageId(SearchResponse response) {
        Map<Long, VerifiedMessage> messages = new LinkedHashMap<>();
        for (VerifiedMessage message : response.verifiedMessages()) {
            messages.put(message.candidate().id(), message);
        }
        return messages;
    }

    private List<ComparedMessage> difference(Map<Long, VerifiedMessage> left, Map<Long, VerifiedMessage> right, boolean includeBroken) {
        List<ComparedMessage> messages = new ArrayList<>();
        for (Map.Entry<Long, VerifiedMessage> entry : left.entrySet()) {
            if (!right.containsKey(entry.getKey())) {
                messages.add(toComparedMessage(entry.getValue(), includeBroken));
            }
        }
        return List.copyOf(messages);
    }

    private ComparedMessage toComparedMessage(VerifiedMessage message, boolean includeBroken) {
        SearchCandidate candidate = message.candidate();
        List<String> fields = new ArrayList<>();
        List<String> policies = new ArrayList<>();
        long visible = 0;
        long hidden = 0;
        String preview = null;
        for (FieldMatch match : message.matches()) {
            boolean broken = isBroken(candidate, match);
            if (!includeBroken && broken) {
                hidden++;
                continue;
            }
            visible++;
            addUnique(fields, match.field());
            addUnique(policies, match.matchPolicy().name());
            if (preview == null) {
                preview = TextPreviewer.preview(match.context(), PREVIEW_CHARS);
            }
        }
        if (preview == null && !message.matches().isEmpty()) {
            FieldMatch first = message.matches().get(0);
            preview = TextPreviewer.preview(first.context(), PREVIEW_CHARS);
        }
        return new ComparedMessage(
                candidate.id(),
                candidate.subject(),
                candidate.senderName(),
                candidate.receivedAt(),
                List.copyOf(fields),
                List.copyOf(policies),
                visible,
                hidden,
                preview == null ? "<none>" : preview
        );
    }

    private void addUnique(List<String> values, String value) {
        if (value != null && !values.contains(value)) {
            values.add(value);
        }
    }

    private boolean isBroken(SearchCandidate candidate, FieldMatch match) {
        SearchField field = Arrays.stream(SearchField.values())
                .filter(value -> value.columnName().equalsIgnoreCase(match.field())
                        || value.displayName().equalsIgnoreCase(match.field()))
                .findFirst()
                .orElse(null);
        return qualityAnalyzer.diagnose(field == null ? null : candidate.fieldValue(field)).level() == TextQualityLevel.BROKEN;
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.replace('\r', ' ').replace('\n', ' ');
    }
}
