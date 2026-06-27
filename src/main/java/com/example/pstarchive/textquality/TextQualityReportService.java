package com.example.pstarchive.textquality;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TextQualityReportService {
    private static final int MAX_EXAMPLES = 10;

    private final TextQualityAnalyzer analyzer = new TextQualityAnalyzer();

    public TextQualityReport analyze(Path storePath, int limit) throws SQLException {
        int effectiveLimit = Math.max(1, limit);
        String sql = """
                SELECT id, folder_path, subject, subject_status, sender_name, sender_email,
                       recipients, cc, body_text, body_text_status, body_html_text, body_html_text_status
                FROM messages
                ORDER BY id
                LIMIT ?
                """;
        Counts counts = new Counts();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + storePath.toAbsolutePath().normalize());
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, effectiveLimit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    counts.messagesChecked++;
                    long messageId = resultSet.getLong("id");
                    check(counts, messageId, "folder_path", null, resultSet.getString("folder_path"));
                    check(counts, messageId, "subject", resultSet.getString("subject_status"), resultSet.getString("subject"));
                    check(counts, messageId, "sender_name", null, resultSet.getString("sender_name"));
                    check(counts, messageId, "sender_email", null, resultSet.getString("sender_email"));
                    check(counts, messageId, "recipients", null, resultSet.getString("recipients"));
                    check(counts, messageId, "cc", null, resultSet.getString("cc"));
                    check(counts, messageId, "body_text", resultSet.getString("body_text_status"), resultSet.getString("body_text"));
                    check(counts, messageId, "body_html_text", resultSet.getString("body_html_text_status"), resultSet.getString("body_html_text"));
                }
            }
        }
        return counts.toReport();
    }

    private void check(Counts counts, long messageId, String field, String storedStatus, String value) {
        TextQualityResult quality = analyzer.diagnose(value);
        counts.fieldsChecked++;
        switch (quality.level()) {
            case OK -> counts.okFields++;
            case SUSPECT -> counts.suspectFields++;
            case DEGRADED -> counts.degradedFields++;
            case BROKEN -> counts.brokenFields++;
            case NULL -> counts.nullFields++;
        }
        if (quality.nulCharCount() > 0) {
            counts.nulCharFields++;
        }
        if (quality.hasWarning("high_question_mark_ratio") || quality.hasWarning("repeated_question_marks")) {
            counts.questionHeavyFields++;
        }
        if (quality.hasWarning("mojibake_pattern")) {
            counts.mojibakeFields++;
        }
        boolean mismatch = isStatusMismatch(storedStatus, quality.level());
        if (mismatch) {
            counts.statusMismatchCount++;
            if (counts.examples.size() < MAX_EXAMPLES) {
                counts.examples.add(new TextFieldDiagnostic(messageId, field, storedStatus, quality, true, value));
            }
        }
    }

    private boolean isStatusMismatch(String storedStatus, TextQualityLevel level) {
        if (storedStatus == null || storedStatus.isBlank()) {
            return false;
        }
        if (!"OK".equalsIgnoreCase(storedStatus)) {
            return false;
        }
        return level == TextQualityLevel.SUSPECT || level == TextQualityLevel.DEGRADED || level == TextQualityLevel.BROKEN;
    }

    private static class Counts {
        long messagesChecked;
        long fieldsChecked;
        long okFields;
        long suspectFields;
        long degradedFields;
        long brokenFields;
        long nullFields;
        long nulCharFields;
        long questionHeavyFields;
        long mojibakeFields;
        long statusMismatchCount;
        List<TextFieldDiagnostic> examples = new ArrayList<>();

        TextQualityReport toReport() {
            return new TextQualityReport(messagesChecked, fieldsChecked, okFields, suspectFields, degradedFields,
                    brokenFields, nullFields, nulCharFields, questionHeavyFields, mojibakeFields,
                    statusMismatchCount, List.copyOf(examples));
        }
    }
}
