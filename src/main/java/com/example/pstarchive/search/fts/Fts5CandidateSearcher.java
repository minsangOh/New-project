package com.example.pstarchive.search.fts;

import com.example.pstarchive.search.CandidateSearchEngine;
import com.example.pstarchive.search.NormalizedQuery;
import com.example.pstarchive.search.SearchCandidate;
import com.example.pstarchive.search.SearchField;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Fts5CandidateSearcher implements CandidateSearchEngine {
    private final Fts5QueryBuilder queryBuilder;

    public Fts5CandidateSearcher() {
        this(new Fts5QueryBuilder());
    }

    public Fts5CandidateSearcher(Fts5QueryBuilder queryBuilder) {
        this.queryBuilder = queryBuilder;
    }

    @Override
    public List<SearchCandidate> search(Path storePath, NormalizedQuery query, List<SearchField> fields, int limit) throws Exception {
        int candidateLimit = Math.max(100, Math.max(1, limit) * 100);
        String ftsQuery = queryBuilder.build(query, fields);
        String sql = """
                SELECT m.id, m.folder_path, m.descriptor_node_id, m.internet_message_id,
                       m.subject, m.subject_status, m.sender_name, m.sender_email, m.recipients, m.cc,
                       m.sent_at, m.received_at, m.body_text, m.body_text_status,
                       m.body_html_text, m.body_html_text_status
                FROM messages_fts
                JOIN messages m ON m.id = messages_fts.rowid
                WHERE messages_fts MATCH ?
                ORDER BY m.received_at DESC, m.id DESC
                LIMIT ?
                """;
        List<SearchCandidate> candidates = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + storePath.toAbsolutePath().normalize())) {
            Fts5Schema.ensureMessagesTableExists(connection);
            if (!Fts5Schema.tableExists(connection, Fts5Schema.TABLE_NAME)) {
                throw new IllegalStateException("FTS5 index not found. Run build-search-index first.");
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, ftsQuery);
                statement.setInt(2, candidateLimit);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        candidates.add(map(resultSet));
                    }
                }
            }
        } catch (SQLException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.toLowerCase(java.util.Locale.ROOT).contains("messages_fts")) {
                throw new IllegalStateException("FTS5 index not found. Run build-search-index first.", e);
            }
            if (message.toLowerCase(java.util.Locale.ROOT).contains("fts5")
                    || message.toLowerCase(java.util.Locale.ROOT).contains("match")) {
                throw new IllegalStateException("FTS5 candidate search failed. Use --engine like or rebuild the FTS5 index. Detail: " + message, e);
            }
            throw e;
        }
        return candidates;
    }

    @Override
    public String name() {
        return "fts5";
    }

    private SearchCandidate map(ResultSet resultSet) throws SQLException {
        return new SearchCandidate(
                resultSet.getLong("id"),
                resultSet.getString("folder_path"),
                nullableLong(resultSet, "descriptor_node_id"),
                resultSet.getString("internet_message_id"),
                resultSet.getString("subject"),
                resultSet.getString("subject_status"),
                resultSet.getString("sender_name"),
                resultSet.getString("sender_email"),
                resultSet.getString("recipients"),
                resultSet.getString("cc"),
                resultSet.getString("sent_at"),
                resultSet.getString("received_at"),
                resultSet.getString("body_text"),
                resultSet.getString("body_text_status"),
                resultSet.getString("body_html_text"),
                resultSet.getString("body_html_text_status")
        );
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
