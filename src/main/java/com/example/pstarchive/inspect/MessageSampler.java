package com.example.pstarchive.inspect;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MessageSampler {
    public List<MessageSample> sample(Path storePath, int limit) throws SQLException {
        int normalizedLimit = Math.max(0, limit);
        List<MessageSample> samples = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + storePath.toAbsolutePath().normalize());
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, folder_path, descriptor_node_id, subject, subject_status,
                            sender_name, sender_email, recipients, cc, sent_at, received_at,
                            body_text_length, body_html_length, body_html_text_length,
                            body_text_status, body_html_status, body_html_text_status,
                            body_text, body_html_text
                     FROM messages ORDER BY id LIMIT ?
                     """)) {
            statement.setInt(1, normalizedLimit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    samples.add(new MessageSample(
                            resultSet.getLong("id"),
                            resultSet.getString("folder_path"),
                            nullableLong(resultSet, "descriptor_node_id"),
                            resultSet.getString("subject"),
                            resultSet.getString("subject_status"),
                            resultSet.getString("sender_name"),
                            resultSet.getString("sender_email"),
                            resultSet.getString("recipients"),
                            resultSet.getString("cc"),
                            resultSet.getString("sent_at"),
                            resultSet.getString("received_at"),
                            nullableInteger(resultSet, "body_text_length"),
                            nullableInteger(resultSet, "body_html_length"),
                            nullableInteger(resultSet, "body_html_text_length"),
                            resultSet.getString("body_text_status"),
                            resultSet.getString("body_html_status"),
                            resultSet.getString("body_html_text_status"),
                            resultSet.getString("body_text"),
                            resultSet.getString("body_html_text")
                    ));
                }
            }
        }
        return samples;
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }
}
