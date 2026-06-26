package com.example.pstarchive.inspect;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class StoreQualityReporter {
    public StoreQualityReport report(Path storePath) throws Exception {
        StoreInspectionResult inspection = new StoreInspector().inspect(storePath);
        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> blocking = new ArrayList<>();

        long messages = inspection.messagesCount();
        long bodyAvailable = bodyAvailableCount(storePath);
        double bodyCoverage = messages == 0 ? 0.0 : bodyAvailable * 100.0 / messages;
        long fatalErrors = inspection.latestRun().map(LatestIndexRun::fatalErrors).orElse(0L);
        long unrecoverable = inspection.latestRun().map(LatestIndexRun::unrecoverableFields).orElse(statusCount(inspection.subjectStatusCounts(), "UNRECOVERABLE"));

        reasons.add("messagesSaved: " + messages);
        reasons.add("folders: " + inspection.foldersCount());
        reasons.add("fatalErrors: " + fatalErrors);
        reasons.add("unrecoverableFields: " + unrecoverable);
        reasons.add(String.format(java.util.Locale.ROOT, "body text exists in %.1f%% messages", bodyCoverage));

        if (messages <= 0) {
            blocking.add("No messages stored");
        }
        if (inspection.foldersCount() <= 0) {
            blocking.add("No folders stored");
        }
        if (fatalErrors > 0) {
            blocking.add("Fatal errors were recorded in the latest index run");
        }
        if (messages > 0 && bodyCoverage < 20.0) {
            blocking.add("Body text/html_text coverage is below 20%");
        }
        if (messages > 0 && unrecoverable > Math.max(10, messages / 20)) {
            blocking.add("Unrecoverable fields are too frequent");
        }

        long degraded = inspection.latestRun().map(LatestIndexRun::degradedFields).orElse(0L);
        long nullFields = inspection.latestRun().map(LatestIndexRun::nullFields).orElse(0L);
        if (degraded > 0) {
            warnings.add(degraded + " degraded fields found");
        }
        if (nullFields > 0) {
            warnings.add(nullFields + " null fields found");
        }
        if (inspection.indexErrorsCount() > 0) {
            warnings.add(inspection.indexErrorsCount() + " index_errors rows found");
        }

        StoreQualityDecision decision;
        if (!blocking.isEmpty()) {
            decision = StoreQualityDecision.NOT_READY;
        } else if (!warnings.isEmpty()) {
            decision = StoreQualityDecision.READY_WITH_WARNINGS;
        } else {
            decision = StoreQualityDecision.READY;
        }
        return new StoreQualityReport(decision, inspection, bodyCoverage, reasons, warnings, blocking, topErrors(storePath));
    }

    private long bodyAvailableCount(Path storePath) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + storePath.toAbsolutePath().normalize());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT COUNT(*) FROM messages
                     WHERE (body_text IS NOT NULL AND body_text != '')
                        OR (body_html_text IS NOT NULL AND body_html_text != '')
                     """)) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
        }
    }

    private List<String> topErrors(Path storePath) throws Exception {
        List<String> errors = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + storePath.toAbsolutePath().normalize());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT stage, field_name, error_type, COUNT(*) count
                     FROM index_errors
                     GROUP BY stage, field_name, error_type
                     ORDER BY count DESC
                     LIMIT 20
                     """)) {
            while (resultSet.next()) {
                errors.add(resultSet.getLong("count") + "x "
                        + resultSet.getString("stage") + "/"
                        + resultSet.getString("field_name") + " "
                        + resultSet.getString("error_type"));
            }
        }
        return errors;
    }

    private long statusCount(List<StatusCount> counts, String status) {
        return counts.stream()
                .filter(count -> status.equals(count.status()))
                .mapToLong(StatusCount::count)
                .sum();
    }
}

