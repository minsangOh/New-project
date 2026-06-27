package com.example.pstarchive;

import com.example.pstarchive.cli.ArchiveCommand;
import com.example.pstarchive.cli.CommandOutput;
import com.example.pstarchive.encoding.ExtractedText;
import com.example.pstarchive.encoding.TextRecoveryStatus;
import com.example.pstarchive.index.MessageStoreWriter;
import com.example.pstarchive.index.PstIndexSummary;
import com.example.pstarchive.index.ShardStoreSchema;
import com.example.pstarchive.inspect.MessageDetailReader;
import com.example.pstarchive.pst.ExtractedFolder;
import com.example.pstarchive.pst.ExtractedMail;
import com.example.pstarchive.textquality.HtmlBodyTextComparator;
import com.example.pstarchive.textquality.HtmlTextDamageType;
import com.example.pstarchive.textquality.StoredTextSanitizer;
import com.example.pstarchive.textquality.TextQualityAnalyzer;
import com.example.pstarchive.textquality.TextQualityFormatter;
import com.example.pstarchive.textquality.TextQualityLevel;
import com.example.pstarchive.textquality.TextQualityReport;
import com.example.pstarchive.textquality.TextQualityReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextQualityDiagnosticsTest {
    private static final String SAMSUNG = "\uC0BC\uC131\uC804\uC790";
    private static final String WATER_PURIFIER = "\uC5BC\uC74C\uC815\uC218\uAE30";
    private static final String MOJIBAKE_SAMPLE = "?\uC88E\uB8DE?\uC1FF\uB71D?\uC208\uC095";
    private static final String KIM_DEOK_YONG = "\uAE40\uB355\uC6A9";
    private static final String OH_MIN_SANG = "\uC624\uBBFC\uC0C1";
    private static final String PARK_JIN_SU = "\uBC15\uC9C4\uC218";
    private static final String HA_GEUN_JU = "\uD558\uADFC\uC8FC";
    private static final String YOON_YOUNG_CHUN = "\uC724\uC601\uCD98";
    private static final String DEPARTMENTS = "\uAC1C\uBC1C\uD300; \uD488\uC9C8\uD300; \uC81C\uD488\uC6B4\uC601\uD300";

    @TempDir
    Path tempDir;

    @Test
    void sanitizerRemovesNulCharactersWithoutHurtingNormalText() {
        assertEquals("Microsoft Outlook", StoredTextSanitizer.sanitize("M\u0000i\u0000c\u0000r\u0000o\u0000s\u0000o\u0000f\u0000t\u0000 \u0000O\u0000u\u0000t\u0000l\u0000o\u0000o\u0000k"));
        assertEquals("Microsoft Outlook", StoredTextSanitizer.sanitize("Microsoft Outlook"));
    }

    @Test
    void analyzerFlagsQuestionHeavyAndMojibakeButKeepsKoreanPartNumbersOk() {
        TextQualityAnalyzer analyzer = new TextQualityAnalyzer();

        assertEquals(TextQualityLevel.OK, analyzer.diagnose(SAMSUNG + " " + WATER_PURIFIER + " DA96-01767C").level());
        assertTrue(isBad(analyzer.diagnose("?????. ?????????.").level()));
        assertTrue(isBad(analyzer.diagnose(MOJIBAKE_SAMPLE).level()));
    }


    @Test
    void analyzerKeepsNormalShortKoreanNamesAndDepartmentsOk() {
        TextQualityAnalyzer analyzer = new TextQualityAnalyzer();
        for (String value : List.of(KIM_DEOK_YONG, OH_MIN_SANG, PARK_JIN_SU, HA_GEUN_JU, YOON_YOUNG_CHUN, DEPARTMENTS)) {
            var result = analyzer.diagnose(value);
            assertEquals(TextQualityLevel.OK, result.level(), value);
            assertTrue(result.warnings().isEmpty(), value);
        }
    }

    @Test
    void warninglessTextIsNotDowngradedToDegraded() {
        TextQualityAnalyzer analyzer = new TextQualityAnalyzer();

        var result = analyzer.diagnose(KIM_DEOK_YONG);

        assertTrue(result.warnings().isEmpty());
        assertNotEquals(TextQualityLevel.DEGRADED, result.level());
        assertNotEquals("degraded_text_signals", result.reason());
    }

    @Test
    void htmlComparisonSeparatesExtractionProblemFromBrokenSource() {
        HtmlBodyTextComparator comparator = new HtmlBodyTextComparator();
        String normalHtml = "<html><head><meta charset=\"utf-8\"></head><body>" + SAMSUNG + " RWP90H</body></html>";

        assertEquals(HtmlTextDamageType.HTML_TO_TEXT_EXTRACTION_PROBLEM,
                comparator.compare(normalHtml, "???????? RWP90H ????????").damageType());
        assertEquals(HtmlTextDamageType.SOURCE_OR_DECODING_BROKEN,
                comparator.compare("<html><body>???????? RWP90H ????????</body></html>", "???????? RWP90H ????????").damageType());
        assertEquals("utf-8", comparator.compare(normalHtml, "???????? RWP90H ????????").detectedCharset());
    }
    @Test
    void writerSanitizesSearchFieldsAndDowngradesClearlyBrokenStoredText() throws Exception {
        Path store = createStore(false);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + store.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            assertEquals("Microsoft Outlook", stringValue(statement, "SELECT sender_name FROM messages WHERE descriptor_node_id = 160"));
            assertEquals(0, intValue(statement, "SELECT instr(sender_name, char(0)) FROM messages WHERE descriptor_node_id = 160"));
            assertEquals("DEGRADED", stringValue(statement, "SELECT body_html_text_status FROM messages WHERE descriptor_node_id = 160"));
        }
    }

    @Test
    void qualityReportFindsStatusMismatchExamples() throws Exception {
        Path store = createStore(true);

        TextQualityReport report = new TextQualityReportService().analyze(store, 100);

        assertTrue(report.messagesChecked() >= 1);
        assertTrue(report.questionHeavyFields() >= 1);
        assertTrue(report.statusMismatchCount() >= 1);
        assertFalse(report.mismatchExamples().isEmpty());
    }

    @Test
    void rawDumpOutputContainsCountersAndCodePoints() throws Exception {
        Path store = createStore(false);
        var detail = new MessageDetailReader().findById(store, 1).orElseThrow();
        Path output = tempDir.resolve("message-raw.txt");

        int exit = CommandOutput.write(output, out -> new TextQualityFormatter(out).printRawDump(detail));
        String text = Files.readString(output);

        assertEquals(0, exit);
        assertTrue(text.contains("MESSAGE RAW DUMP"));
        assertTrue(text.contains("nulCharCount"));
        assertTrue(text.contains("codePoints"));
        assertTrue(text.contains("BODY HTML TEXT COMPARISON"));
        assertTrue(text.contains("bodyHtmlDetectedCharset: ks_c_5601-1987"));
    }

    @Test
    void diagnosticCommandsAreRegistered() {
        CommandLine commandLine = new CommandLine(new ArchiveCommand());

        assertTrue(commandLine.getSubcommands().containsKey("diagnose-text-quality"));
        assertTrue(commandLine.getSubcommands().containsKey("dump-message-raw"));
        assertDoesNotThrow(() -> commandLine.parseArgs("diagnose-text-quality", "store.sqlite", "--limit", "100", "--output", "quality.txt"));
        assertDoesNotThrow(() -> commandLine.parseArgs("dump-message-raw", "store.sqlite", "--id", "160", "--output", "raw.txt"));
    }

    private boolean isBad(TextQualityLevel level) {
        return level == TextQualityLevel.SUSPECT || level == TextQualityLevel.DEGRADED || level == TextQualityLevel.BROKEN;
    }

    private Path createStore(boolean forceMismatch) throws Exception {
        Path store = tempDir.resolve("quality-" + System.nanoTime() + ".sqlite");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + store.toAbsolutePath())) {
            ShardStoreSchema.migrate(connection);
            connection.setAutoCommit(false);
            try (MessageStoreWriter writer = new MessageStoreWriter(connection)) {
                long runId = writer.startRun("sample.pst", 10, true);
                long folderId = writer.writeFolder(new ExtractedFolder(null, null, "/Root/Inbox", "Inbox", 10L, 1, 0));
                writer.writeMessage(mail(folderId));
                writer.finishRun(runId, new PstIndexSummary(1, 1, 1, 0, 0, 0, 8, 0, 0, 0, 0, 10, "SUCCESS"));
            }
            if (forceMismatch) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("UPDATE messages SET subject = '?????. ?????????.', subject_status = 'OK' WHERE descriptor_node_id = 160");
                }
            }
            connection.commit();
        }
        return store;
    }

    private ExtractedMail mail(long folderId) {
        return new ExtractedMail(
                folderId,
                "/Root/Inbox",
                160L,
                "message-160",
                text("RWP90H DA96-01767C"),
                text("M\u0000i\u0000c\u0000r\u0000o\u0000s\u0000o\u0000f\u0000t\u0000 \u0000O\u0000u\u0000t\u0000l\u0000o\u0000o\u0000k"),
                "sender@example.com",
                text("me@example.com"),
                ExtractedText.nullValue(),
                "2026-06-26T10:00:00+09:00",
                "2026-06-26T10:01:00+09:00",
                text(SAMSUNG + " " + WATER_PURIFIER + " DA96-01767C"),
                text("<html><head><meta charset=\"ks_c_5601-1987\"></head><body>?????. ?????????.</body></html>"),
                text("?????. ?????????."),
                "OK",
                List.of()
        );
    }

    private ExtractedText text(String value) {
        return new ExtractedText(value, TextRecoveryStatus.OK, "fixture", null, null);
    }

    private String stringValue(Statement statement, String sql) throws Exception {
        try (var resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private int intValue(Statement statement, String sql) throws Exception {
        try (var resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getInt(1) : -1;
        }
    }
}
