package com.example.pstarchive;

import com.example.pstarchive.cli.ArchiveCommand;
import com.example.pstarchive.cli.CommandOutput;
import com.example.pstarchive.encoding.ExtractedText;
import com.example.pstarchive.encoding.TextRecoveryStatus;
import com.example.pstarchive.index.MessageStoreWriter;
import com.example.pstarchive.index.PstIndexSummary;
import com.example.pstarchive.index.ShardStoreSchema;
import com.example.pstarchive.pst.ExtractedFolder;
import com.example.pstarchive.pst.ExtractedMail;
import com.example.pstarchive.search.CandidateSearchEngine;
import com.example.pstarchive.search.MatchLocator;
import com.example.pstarchive.search.MatchPolicy;
import com.example.pstarchive.search.NormalizedQuery;
import com.example.pstarchive.search.RawFieldVerifier;
import com.example.pstarchive.search.SearchField;
import com.example.pstarchive.search.SearchCandidate;
import com.example.pstarchive.search.SearchQueryNormalizer;
import com.example.pstarchive.search.SearchResponse;
import com.example.pstarchive.search.SearchResultFormatter;
import com.example.pstarchive.search.SearchStoreService;
import com.example.pstarchive.search.fts.Fts5CandidateSearcher;
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

class SearchStoreTest {
    private static final String SAMSUNG = "\uC0BC\uC131\uC804\uC790";
    private static final String WATER_PURIFIER = "\uC5BC\uC74C\uC815\uC218\uAE30";

    @TempDir
    Path tempDir;

    @Test
    void searchesSubjectSenderRecipientsFolderAndBody() throws Exception {
        Path store = createSearchStore();
        SearchStoreService service = new SearchStoreService();

        assertEquals(1, service.search(store, "RWP90H", 20, 80, 5, List.of(SearchField.SUBJECT)).verifiedMessages().size());
        assertEquals(1, service.search(store, "hong@example.com", 20, 80, 5, SearchField.allSearchable()).verifiedMessages().size());
        assertEquals(1, service.search(store, "team@example.com", 20, 80, 5, SearchField.allSearchable()).verifiedMessages().size());
        assertEquals(2, service.search(store, "Samsung", 20, 80, 5, SearchField.allSearchable()).verifiedMessages().size());
        assertEquals(1, service.search(store, WATER_PURIFIER, 20, 80, 5, SearchField.allSearchable()).verifiedMessages().size());
    }

    @Test
    void searchesPartNumbersAndKorean() throws Exception {
        Path store = createSearchStore();
        SearchStoreService service = new SearchStoreService();

        assertEquals(1, service.search(store, "DA96-01139A", 20, 80, 5, SearchField.allSearchable()).verifiedMessages().size());
        assertEquals(1, service.search(store, SAMSUNG, 20, 80, 5, SearchField.allSearchable()).verifiedMessages().size());
    }

    @Test
    void fts5SearchesRwp90hKoreanAndPartNumbers() throws Exception {
        Path store = createSearchStore();
        new Fts5IndexBuilder().build(store, true);
        SearchStoreService service = fts5Service();

        assertEquals(2, service.search(store, "RWP90H", 20, 80, 5, SearchField.allSearchable()).verifiedMessages().size());
        assertEquals(1, service.search(store, SAMSUNG, 20, 80, 5, SearchField.allSearchable()).verifiedMessages().size());
        assertEquals(1, service.search(store, WATER_PURIFIER, 20, 80, 5, SearchField.allSearchable()).verifiedMessages().size());
        assertEquals(1, service.search(store, "DA96-01139A", 20, 80, 5, SearchField.allSearchable()).verifiedMessages().size());
    }

    @Test
    void fts5StillRequiresRawFieldVerification() throws Exception {
        Path store = createSearchStore();
        new Fts5IndexBuilder().build(store, true);

        SearchResponse response = fts5Service().search(store, "false positive token", 20, 80, 5, List.of(SearchField.SUBJECT));

        assertTrue(response.sqlCandidates() >= 1);
        assertEquals(0, response.verifiedMessages().size());
    }

    @Test
    void fts5RequiresExistingIndex() throws Exception {
        Path store = createSearchStore();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fts5Service().search(store, "RWP90H", 20, 80, 5, SearchField.allSearchable()));

        assertTrue(error.getMessage().contains("FTS5 index not found. Run build-search-index first."));
    }

    @Test
    void fts5HonorsSubjectFieldRestriction() throws Exception {
        Path store = createSearchStore();
        new Fts5IndexBuilder().build(store, true);
        SearchStoreService service = fts5Service();

        assertEquals(1, service.search(store, "RWP90H", 20, 80, 5, List.of(SearchField.SUBJECT)).verifiedMessages().size());
        assertEquals(0, service.search(store, "DA96-01139A", 20, 80, 5, List.of(SearchField.SUBJECT)).verifiedMessages().size());
    }

    @Test
    void excludesSqlCandidateWhenRawVerificationFails() throws Exception {
        Path store = createSearchStore();
        SearchStoreService service = new SearchStoreService();

        SearchResponse response = service.search(store, "false positive token", 20, 80, 5, List.of(SearchField.SUBJECT));

        assertTrue(response.sqlCandidates() >= 1);
        assertEquals(0, response.verifiedMessages().size());
    }

    @Test
    void supportsCaseAndWhitespaceInsensitiveMatches() throws Exception {
        Path store = createSearchStore();
        SearchStoreService service = new SearchStoreService();

        assertEquals(1, service.search(store, "rwp90h", 20, 80, 5, List.of(SearchField.SUBJECT)).verifiedMessages().size());
        assertEquals(1, service.search(store, "wire harness", 20, 80, 5, SearchField.allSearchable()).verifiedMessages().size());
    }


    @Test
    void searchStoreServiceUsesInjectedCandidateSearchEngine() throws Exception {
        Path store = Files.createFile(tempDir.resolve("fake-store.sqlite"));
        CandidateSearchEngine engine = new CandidateSearchEngine() {
            @Override
            public List<SearchCandidate> search(Path storePath, NormalizedQuery query, List<SearchField> fields, int limit) {
                return List.of(new SearchCandidate(
                        900L,
                        "/Root/Inbox",
                        12345L,
                        "message-12345",
                        "RWP90H from injected engine",
                        "OK",
                        "sender",
                        "sender@example.com",
                        "me@example.com",
                        null,
                        "2026-06-18T14:22:00+09:00",
                        "2026-06-18T14:23:00+09:00",
                        null,
                        "NULL",
                        null,
                        "NULL"
                ));
            }

            @Override
            public String name() {
                return "fake";
            }
        };

        SearchResponse response = new SearchStoreService(new SearchQueryNormalizer(), engine, new RawFieldVerifier())
                .search(store, "RWP90H", 20, 80, 5, List.of(SearchField.SUBJECT));

        assertEquals(1, response.sqlCandidates());
        assertEquals(1, response.verifiedMessages().size());
        assertEquals("subject", response.verifiedMessages().get(0).matches().get(0).field());
    }
    @Test
    void computesLineParagraphContextAndMultipleMatches() {
        SearchQueryNormalizer normalizer = new SearchQueryNormalizer();
        NormalizedQuery query = normalizer.normalize("RWP90H");
        String text = "intro\n\nparagraph RWP90H first\nline RWP90H second";

        var matches = new MatchLocator().locate("body_html_text", text, query, 10, 5, normalizer);

        assertEquals(2, matches.size());
        assertEquals(3, matches.get(0).lineNumber());
        assertEquals(2, matches.get(0).paragraphNumber());
        assertTrue(matches.get(0).context().contains("[RWP90H]"));
        assertEquals(MatchPolicy.EXACT, matches.get(0).matchPolicy());
    }

    @Test
    void rejectsBlankQuery() {
        assertThrows(IllegalArgumentException.class, () -> new SearchQueryNormalizer().normalize("   "));
    }

    @Test
    void outputFileIsUtf8() throws Exception {
        Path output = tempDir.resolve("search-report.txt");

        int exit = CommandOutput.write(output, out -> out.println("search"));

        assertEquals(0, exit);
        byte[] bytes = Files.readAllBytes(output);
        assertEquals((byte) 0xEF, bytes[0]);
    }

    @Test
    void archiveCommandExposesSearchStore() {
        CommandLine commandLine = new CommandLine(new ArchiveCommand());

        assertTrue(commandLine.getSubcommands().containsKey("search-store"));
        assertDoesNotThrow(() -> commandLine.parseArgs("search-store", "store.sqlite", "RWP90H", "--limit", "20",
                "--context", "80", "--field", "body", "--engine", "fts5",
                "--max-matches-per-message", "3", "--include-broken", "--output", "out.txt"));
    }


    @Test
    void formatterHidesBrokenMatchesByDefaultAndShowsHiddenCount() throws Exception {
        Path store = createSearchStoreWithBrokenBodyMatch();
        SearchResponse response = new SearchStoreService().search(store, "RWP90H", 20, 80, 5, SearchField.allSearchable());

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new SearchResultFormatter(new PrintStream(buffer, true, StandardCharsets.UTF_8))
                .print(new SearchResultFormatter.PathLabel(store.toString()), response, false);
        String output = buffer.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("hiddenBrokenMatches: 1"));
        assertTrue(output.contains("field: subject"));
        assertFalse(output.contains("field: body_html_text"));
    }

    @Test
    void formatterIncludesBrokenMatchesWhenRequested() throws Exception {
        Path store = createSearchStoreWithBrokenBodyMatch();
        SearchResponse response = new SearchStoreService().search(store, "RWP90H", 20, 80, 5, SearchField.allSearchable());

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new SearchResultFormatter(new PrintStream(buffer, true, StandardCharsets.UTF_8))
                .print(new SearchResultFormatter.PathLabel(store.toString()), response, true);
        String output = buffer.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("hiddenBrokenMatches: 0"));
        assertTrue(output.contains("field: body_html_text"));
        assertTrue(output.contains("textQuality: BROKEN"));
    }

    @Test
    void fts5FormatterKeepsBrokenMatchPolicy() throws Exception {
        Path store = createSearchStoreWithBrokenBodyMatch();
        new Fts5IndexBuilder().build(store, true);
        SearchResponse response = fts5Service().search(store, "RWP90H", 20, 80, 5, SearchField.allSearchable());

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new SearchResultFormatter(new PrintStream(buffer, true, StandardCharsets.UTF_8))
                .print(new SearchResultFormatter.PathLabel(store.toString()), response, false);
        String output = buffer.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("engine: fts5"));
        assertTrue(output.contains("hiddenBrokenMatches: 1"));
        assertTrue(output.contains("field: subject"));
        assertFalse(output.contains("field: body_html_text"));
    }

    private SearchStoreService fts5Service() {
        return new SearchStoreService(new SearchQueryNormalizer(), new Fts5CandidateSearcher(), new RawFieldVerifier());
    }
    private Path createSearchStore() throws Exception {
        Path store = tempDir.resolve("search-store.sqlite");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + store.toAbsolutePath())) {
            ShardStoreSchema.migrate(connection);
            connection.setAutoCommit(false);
            try (MessageStoreWriter writer = new MessageStoreWriter(connection)) {
                long runId = writer.startRun("sample.pst", 10, true);
                long folderId = writer.writeFolder(new ExtractedFolder(null, null, "/Root/Inbox/Samsung/RWP90H", "RWP90H", 10L, 3, 0));
                writer.writeMessage(mail(folderId, 1L,
                        "RWP90H harness development request",
                        "Hong Gil Dong", "hong@example.com", "me@example.com; team@example.com", "copy@example.com",
                        SAMSUNG + " " + WATER_PURIFIER + " DA96-01139A wire    harness body RWP90H\n\nsecond paragraph RWP90H"));
                writer.writeMessage(mail(folderId, 2L,
                        "false-positive-token",
                        "No Match", "nomatch@example.com", "other@example.com", null,
                        "body without target"));
                writer.finishRun(runId, new PstIndexSummary(1, 2, 2, 0, 0, 0, 14, 0, 0, 0, 0, 10, "SUCCESS"));
                connection.commit();
            }
        }
        return store;
    }


    private Path createSearchStoreWithBrokenBodyMatch() throws Exception {
        Path store = tempDir.resolve("broken-search-store.sqlite");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + store.toAbsolutePath())) {
            ShardStoreSchema.migrate(connection);
            connection.setAutoCommit(false);
            try (MessageStoreWriter writer = new MessageStoreWriter(connection)) {
                long runId = writer.startRun("sample.pst", 10, true);
                long folderId = writer.writeFolder(new ExtractedFolder(null, null, "/Root/Inbox", "Inbox", 10L, 1, 0));
                writer.writeMessage(mailWithTexts(folderId, 10L,
                        "RWP90H normal subject",
                        ExtractedText.nullValue(),
                        text("<html><body>?????????? RWP90H ??????????</body></html>"),
                        text("?????????? RWP90H ??????????")));
                writer.finishRun(runId, new PstIndexSummary(1, 1, 1, 0, 0, 0, 2, 1, 0, 0, 0, 10, "SUCCESS"));
                connection.commit();
            }
        }
        return store;
    }
    private ExtractedMail mail(long folderId, long descriptorNodeId, String subject, String senderName, String senderEmail,
                               String recipients, String cc, String body) {
        return new ExtractedMail(
                folderId,
                "/Root/Inbox/Samsung/RWP90H",
                descriptorNodeId,
                "message-" + descriptorNodeId,
                text(subject),
                text(senderName),
                senderEmail,
                text(recipients),
                cc == null ? ExtractedText.nullValue() : text(cc),
                "2026-06-18T14:22:00+09:00",
                "2026-06-18T14:23:00+09:00",
                ExtractedText.nullValue(),
                text("<html><body>" + body + "</body></html>"),
                text(body),
                "OK",
                List.of()
        );
    }


    private ExtractedMail mailWithTexts(long folderId, long descriptorNodeId, String subject, ExtractedText bodyText,
                                        ExtractedText bodyHtml, ExtractedText bodyHtmlText) {
        return new ExtractedMail(
                folderId,
                "/Root/Inbox",
                descriptorNodeId,
                "message-" + descriptorNodeId,
                text(subject),
                text("sender"),
                "sender@example.com",
                text("me@example.com"),
                ExtractedText.nullValue(),
                "2026-06-18T14:22:00+09:00",
                "2026-06-18T14:23:00+09:00",
                bodyText,
                bodyHtml,
                bodyHtmlText,
                "OK",
                List.of()
        );
    }
    private ExtractedText text(String value) {
        return new ExtractedText(value, TextRecoveryStatus.OK, "fixture", null, null);
    }
}
