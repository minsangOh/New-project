package com.example.pstarchive;

import com.example.pstarchive.cli.ArchiveCommand;
import com.example.pstarchive.encoding.ExtractedText;
import com.example.pstarchive.encoding.TextRecoveryStatus;
import com.example.pstarchive.index.MessageStoreWriter;
import com.example.pstarchive.index.PstIndexSummary;
import com.example.pstarchive.index.ShardStoreSchema;
import com.example.pstarchive.pst.ExtractedFolder;
import com.example.pstarchive.pst.ExtractedMail;
import com.example.pstarchive.search.SearchEngineType;
import com.example.pstarchive.search.benchmark.BenchmarkCounts;
import com.example.pstarchive.search.benchmark.BenchmarkEngineResult;
import com.example.pstarchive.search.benchmark.BenchmarkReport;
import com.example.pstarchive.search.benchmark.BenchmarkSearchFormatter;
import com.example.pstarchive.search.benchmark.BenchmarkSearchService;
import com.example.pstarchive.search.fts.Fts5IndexBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkSearchTest {
    private static final String SAMSUNG = "\uC0BC\uC131\uC804\uC790";
    private static final String WATER_PURIFIER = "\uC5BC\uC74C\uC815\uC218\uAE30";

    @TempDir
    Path tempDir;

    @Test
    void archiveCommandExposesBenchmarkSearchOptions() {
        CommandLine commandLine = new CommandLine(new ArchiveCommand());

        assertTrue(commandLine.getSubcommands().containsKey("benchmark-search"));
        assertDoesNotThrow(() -> commandLine.parseArgs("benchmark-search", "store.sqlite", "RWP90H",
                "--engine", "all", "--limit", "20", "--repeat", "2", "--field", "body",
                "--include-broken", "--output", "benchmark.txt"));
    }

    @Test
    void runsLikeBenchmark() throws Exception {
        Path store = createStore();

        BenchmarkReport report = new BenchmarkSearchService()
                .benchmark(store, "RWP90H", "all", 20, 2, false, List.of(SearchEngineType.LIKE));

        assertFalse(report.hasFailure());
        assertEquals(1, report.results().size());
        assertEquals("like", report.results().get(0).engine());
        assertEquals(2, report.results().get(0).completedRuns());
        assertEquals(1, report.results().get(0).counts().verifiedMessages());
    }

    @Test
    void runsFts5Benchmark() throws Exception {
        Path store = createStore();
        new Fts5IndexBuilder().build(store, true);

        BenchmarkReport report = new BenchmarkSearchService()
                .benchmark(store, WATER_PURIFIER, "all", 20, 1, false, List.of(SearchEngineType.FTS5));

        assertFalse(report.hasFailure());
        assertEquals("fts5", report.results().get(0).engine());
        assertEquals(1, report.results().get(0).completedRuns());
        assertEquals(1, report.results().get(0).counts().verifiedMessages());
    }

    @Test
    void runsHybridBenchmark() throws Exception {
        Path store = createStore();
        new Fts5IndexBuilder().build(store, true);

        BenchmarkReport report = new BenchmarkSearchService()
                .benchmark(store, "RWP90H", "all", 20, 1, false, List.of(SearchEngineType.HYBRID));

        assertFalse(report.hasFailure());
        assertEquals("hybrid", report.results().get(0).engine());
        assertEquals(1, report.results().get(0).completedRuns());
        assertEquals(1, report.results().get(0).counts().verifiedMessages());
    }

    @Test
    void allEngineOutputsLikeFts5AndHybrid() throws Exception {
        assertEquals(List.of(SearchEngineType.LIKE, SearchEngineType.FTS5, SearchEngineType.HYBRID),
                com.example.pstarchive.search.benchmark.BenchmarkEngineSelection.fromOption("all").engines());
    }
    @Test
    void bothEngineOutputsBothResultsAndRepeat() throws Exception {
        Path store = createStore();
        new Fts5IndexBuilder().build(store, true);

        BenchmarkReport report = new BenchmarkSearchService()
                .benchmark(store, "DA96-01767C", "all", 20, 2, false,
                        List.of(SearchEngineType.LIKE, SearchEngineType.FTS5));

        assertFalse(report.hasFailure());
        assertEquals(2, report.results().size());
        assertEquals("like", report.results().get(0).engine());
        assertEquals("fts5", report.results().get(1).engine());
        assertEquals(2, report.results().get(0).completedRuns());
        assertEquals(2, report.results().get(1).completedRuns());
        assertEquals(report.results().get(0).counts().verifiedMessages(),
                report.results().get(1).counts().verifiedMessages());
    }

    @Test
    void outputFileIsUtf8AndContainsOnlySummary() throws Exception {
        Path store = createStore();
        new Fts5IndexBuilder().build(store, true);
        Path output = tempDir.resolve("benchmark-output.txt");

        int exit = new CommandLine(new ArchiveCommand()).execute("benchmark-search", store.toString(), SAMSUNG,
                "--engine", "both", "--repeat", "2", "--output", output.toString());

        assertEquals(0, exit);
        byte[] bytes = Files.readAllBytes(output);
        assertEquals((byte) 0xEF, bytes[0]);
        String text = Files.readString(output, StandardCharsets.UTF_8);
        assertTrue(text.contains("BENCHMARK SEARCH"));
        assertTrue(text.contains("[ENGINE like]"));
        assertTrue(text.contains("[ENGINE fts5]"));
        assertTrue(text.contains("repeat: 2"));
        assertTrue(text.contains("candidateCount:"));
        assertFalse(text.contains("context:"));
        assertFalse(text.contains("MATCHES"));
    }

    @Test
    void fts5BenchmarkReportsMissingIndexClearly() throws Exception {
        Path store = createStore();

        BenchmarkReport report = new BenchmarkSearchService()
                .benchmark(store, "RWP90H", "all", 20, 1, false, List.of(SearchEngineType.FTS5));

        assertTrue(report.hasFailure());
        assertEquals("FAILED", report.results().get(0).status());
        assertTrue(report.results().get(0).error().contains("FTS5 index not found. Run build-search-index first."));
    }

    @Test
    void commandWritesFailureReportForMissingFts5Index() throws Exception {
        Path store = createStore();
        Path output = tempDir.resolve("missing-fts5.txt");

        int exit = new CommandLine(new ArchiveCommand()).execute("benchmark-search", store.toString(), "RWP90H",
                "--engine", "fts5", "--output", output.toString());

        assertEquals(1, exit);
        String text = Files.readString(output, StandardCharsets.UTF_8);
        assertTrue(text.contains("[ENGINE fts5]"));
        assertTrue(text.contains("status: FAILED"));
        assertTrue(text.contains("FTS5 index not found. Run build-search-index first."));
    }

    @Test
    void formatterKeepsLikeDefaultWhenFts5VerifiedCountDiffers() {
        BenchmarkReport report = new BenchmarkReport(
                tempDir.resolve("store.sqlite"),
                "DA96-01767C",
                "all",
                20,
                3,
                false,
                List.of(
                        new BenchmarkEngineResult("like", 3, 3, "SUCCESS", null,
                                new BenchmarkCounts(11, 11, 11, 11, 11, 0),
                                300, 100, 90, 120),
                        new BenchmarkEngineResult("fts5", 3, 3, "SUCCESS", null,
                                new BenchmarkCounts(9, 9, 9, 9, 9, 0),
                                30, 10, 8, 12)
                )
        );

        String text = format(report);

        assertTrue(text.contains("policyDecision: KEEP_LIKE_DEFAULT"));
        assertTrue(text.contains("reason: fts5_verified_count_diff"));
        assertTrue(text.contains("Do not switch default engine to FTS5 for this store/query set."));
    }

    @Test
    void formatterAllowsCompatibilityHintForEqualCountsOnly() {
        BenchmarkReport report = new BenchmarkReport(
                tempDir.resolve("store.sqlite"),
                "RWP90H",
                "all",
                20,
                3,
                false,
                List.of(
                        new BenchmarkEngineResult("like", 3, 3, "SUCCESS", null,
                                new BenchmarkCounts(7, 7, 7, 18, 15, 3),
                                300, 100, 90, 120),
                        new BenchmarkEngineResult("fts5", 3, 3, "SUCCESS", null,
                                new BenchmarkCounts(7, 7, 7, 18, 15, 3),
                                30, 10, 8, 12)
                )
        );

        String text = format(report);

        assertTrue(text.contains("policyDecision: KEEP_LIKE_DEFAULT"));
        assertTrue(text.contains("reason: compatible_for_this_query_only"));
        assertTrue(text.contains("FTS5 compatible for this query."));
    }

    private String format(BenchmarkReport report) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new BenchmarkSearchFormatter(new PrintStream(buffer, true, StandardCharsets.UTF_8)).print(report);
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private Path createStore() throws Exception {
        Path store = tempDir.resolve("benchmark-store.sqlite");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + store.toAbsolutePath())) {
            ShardStoreSchema.migrate(connection);
            connection.setAutoCommit(false);
            try (MessageStoreWriter writer = new MessageStoreWriter(connection)) {
                long runId = writer.startRun("sample.pst", 10, true);
                long folderId = writer.writeFolder(new ExtractedFolder(null, null, "/Root/Inbox", "Inbox", 10L, 1, 0));
                writer.writeMessage(mail(folderId, 100L,
                        "RWP90H harness " + SAMSUNG,
                        SAMSUNG + " " + WATER_PURIFIER + " DA96-01767C body RWP90H"));
                writer.finishRun(runId, new PstIndexSummary(1, 1, 1, 0, 0, 0, 7, 0, 0, 0, 0, 10, "SUCCESS"));
                connection.commit();
            }
        }
        return store;
    }

    private ExtractedMail mail(long folderId, long descriptorNodeId, String subject, String body) {
        return new ExtractedMail(
                folderId,
                "/Root/Inbox",
                descriptorNodeId,
                "message-" + descriptorNodeId,
                text(subject),
                text("Sender"),
                "sender@example.com",
                text("me@example.com"),
                ExtractedText.nullValue(),
                "2026-06-18T14:22:00+09:00",
                "2026-06-18T14:23:00+09:00",
                text(body),
                text("<html><body>" + body + "</body></html>"),
                text(body),
                "OK",
                List.of()
        );
    }

    private ExtractedText text(String value) {
        return new ExtractedText(value, TextRecoveryStatus.OK, "fixture", null, null);
    }
}
