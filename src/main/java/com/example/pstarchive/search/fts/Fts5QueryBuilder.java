package com.example.pstarchive.search.fts;

import com.example.pstarchive.search.NormalizedQuery;
import com.example.pstarchive.search.SearchField;

import java.util.ArrayList;
import java.util.List;

public class Fts5QueryBuilder {
    public String build(NormalizedQuery query, List<SearchField> fields) {
        List<String> phrases = phrases(query);
        List<String> clauses = new ArrayList<>();
        for (SearchField field : fields) {
            for (String phrase : phrases) {
                clauses.add(field.columnName() + " : " + quote(phrase));
            }
        }
        return String.join(" OR ", clauses);
    }

    private List<String> phrases(NormalizedQuery query) {
        List<String> phrases = new ArrayList<>();
        add(phrases, query.comparable());
        for (String token : query.comparable().split(" ")) {
            if (token.length() >= 2) {
                add(phrases, token);
            }
        }
        return phrases;
    }

    private void add(List<String> phrases, String phrase) {
        if (phrase != null && !phrase.isBlank() && !phrases.contains(phrase)) {
            phrases.add(phrase);
        }
    }

    private String quote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
