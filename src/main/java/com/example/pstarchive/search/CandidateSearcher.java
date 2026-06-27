package com.example.pstarchive.search;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CandidateSearcher {
    public List<SearchCandidate> search(Path storePath, NormalizedQuery query, List<SearchField> fields, int limit) throws SQLException {
        int candidateLimit = Math.max(100, Math.max(1, limit) * 100);
        String where = buildWhere(fields, query.escapedCandidateTerms().size());
        String sql = """
                SELECT id, folder_path, descriptor_node_id, internet_message_id,
                       subject, subject_status, sender_name, sender_email, recipients, cc,
                       sent_at, received_at, body_text, body_text_status,
                       body_html_text, body_html_text_status
                FROM messages
                WHERE 
                """ + where + " ORDER BY received_at DESC, id DESC LIMIT ?";
        List<SearchCandidate> candidates = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + storePath.toAbsolutePath().normalize());
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            for (String term : query.escapedCandidateTerms()) {
                String pattern = "%" + term + "%";
                for (int i = 0; i < fields.size(); i++) {
                    statement.setString(parameter++, pattern);
                }
            }
            statement.setInt(parameter, candidateLimit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    candidates.add(map(resultSet));
                }
            }
        }
        return candidates;
    }

    private String buildWhere(List<SearchField> fields, int termCount) {
        List<String> termClauses = new ArrayList<>();
        for (int term = 0; term < termCount; term++) {
            List<String> fieldClauses = new ArrayList<>();
            for (SearchField field : fields) {
                fieldClauses.add("LOWER(COALESCE(" + field.columnName() + ", '')) LIKE ? ESCAPE '\\'");
            }
            termClauses.add("(" + String.join(" OR ", fieldClauses) + ")");
        }
        return String.join(" OR ", termClauses);
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
