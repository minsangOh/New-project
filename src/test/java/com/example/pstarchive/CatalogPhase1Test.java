package com.example.pstarchive;

import com.example.pstarchive.catalog.CatalogDatabase;
import com.example.pstarchive.catalog.CatalogRepository;
import com.example.pstarchive.fingerprint.PstFingerprintService;
import com.example.pstarchive.model.PstFileRecord;
import com.example.pstarchive.model.PstFingerprint;
import com.example.pstarchive.model.PstStatus;
import com.example.pstarchive.model.ShardManifest;
import com.example.pstarchive.shard.ShardManager;
import com.example.pstarchive.shard.ShardPathResolver;
import com.example.pstarchive.util.AppPaths;
import com.example.pstarchive.util.JsonUtils;
import com.example.pstarchive.util.TimeUtils;
import com.example.pstarchive.verify.ArchiveVerifier;
import com.example.pstarchive.verify.VerifyResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CatalogPhase1Test {
    @TempDir
    Path tempDir;

    @Test
    void createsCatalogDatabase() throws Exception {
        CatalogDatabase database = new CatalogDatabase(tempDir);
        database.initialize();

        assertTrue(Files.exists(AppPaths.catalogDatabase(tempDir)));
        assertTrue(Files.exists(AppPaths.shardsDir(tempDir)));
        assertTrue(Files.exists(AppPaths.logsDir(tempDir)));
    }

    @Test
    void registersPstRecord() throws Exception {
        TestContext context = newContext();
        Path pst = createTempPst("sample.pst", "hello pst");
        PstFileRecord record = createRecord(pst, PstStatus.ACTIVE);

        context.repository.insertPst(record);

        List<PstFileRecord> records = context.repository.listPsts();
        assertEquals(1, records.size());
        assertEquals(record.pstId(), records.getFirst().pstId());
    }

    @Test
    void preventsDuplicateRegistrationByFingerprint() throws Exception {
        TestContext context = newContext();
        Path pst = createTempPst("duplicate.pst", "same content");
        PstFileRecord record = createRecord(pst, PstStatus.ARCHIVE);
        context.repository.insertPst(record);

        PstFingerprint fingerprint = new PstFingerprintService().calculate(pst);

        assertTrue(context.repository.findByFingerprint(fingerprint.fileFingerprint()).isPresent());
    }

    @Test
    void renamedFileKeepsSameFingerprintWhenMetadataIsPreserved() throws Exception {
        Path original = createTempPst("before.pst", "rename me");
        PstFingerprint before = new PstFingerprintService().calculate(original);
        Path renamed = tempDir.resolve("after.pst");

        Files.move(original, renamed, StandardCopyOption.REPLACE_EXISTING);

        PstFingerprint after = new PstFingerprintService().calculate(renamed);
        assertEquals(before.fileFingerprint(), after.fileFingerprint());
    }

    @Test
    void verifyMarksMissingPst() throws Exception {
        TestContext context = newContext();
        Path pst = createTempPst("missing.pst", "temporary");
        PstFileRecord record = createRecord(pst, PstStatus.ARCHIVE);
        context.repository.insertPst(record);
        Files.delete(pst);

        ArchiveVerifier verifier = new ArchiveVerifier(context.repository, new PstFingerprintService());
        VerifyResult result = verifier.verify(record);

        assertFalse(result.exists());
        assertEquals(PstStatus.MISSING, result.resultingStatus());
        assertEquals(PstStatus.MISSING, context.repository.findById(record.pstId()).orElseThrow().status());
    }

    @Test
    void changesStatus() throws Exception {
        TestContext context = newContext();
        Path pst = createTempPst("status.pst", "status");
        PstFileRecord record = createRecord(pst, PstStatus.ARCHIVE);
        context.repository.insertPst(record);

        context.repository.updateStatus(record.pstId(), PstStatus.COLD);

        assertEquals(PstStatus.COLD, context.repository.findById(record.pstId()).orElseThrow().status());
    }

    @Test
    void createsShardManifest() throws Exception {
        TestContext context = newContext();
        Path pst = createTempPst("manifest.pst", "manifest");
        PstFileRecord record = createRecord(pst, PstStatus.WARM);
        context.repository.insertPst(record);

        context.shardManager.createShard(record);

        Path manifestPath = new ShardPathResolver(tempDir).manifestPath(record.pstId());
        assertTrue(Files.exists(manifestPath));
        ShardManifest manifest = JsonUtils.read(manifestPath, ShardManifest.class);
        assertEquals(record.pstId(), manifest.pstId());
        assertEquals("phase1", manifest.storeVersion());
    }

    @Test
    void usesCustomDataDir() throws Exception {
        Path customDir = tempDir.resolve("custom-data");
        CatalogDatabase database = new CatalogDatabase(customDir);
        database.initialize();

        assertTrue(Files.exists(customDir.resolve("catalog").resolve("archive_catalog.sqlite")));
        assertTrue(Files.exists(customDir.resolve("shards")));
    }

    private TestContext newContext() throws Exception {
        CatalogDatabase database = new CatalogDatabase(tempDir);
        database.initialize();
        CatalogRepository repository = new CatalogRepository(database);
        ShardManager shardManager = new ShardManager(new ShardPathResolver(tempDir));
        return new TestContext(repository, shardManager);
    }

    private PstFileRecord createRecord(Path path, PstStatus status) throws Exception {
        PstFingerprint fingerprint = new PstFingerprintService().calculate(path);
        String now = TimeUtils.nowIso();
        return new PstFileRecord(
                UUID.randomUUID().toString(),
                path.getFileName().toString(),
                path.toAbsolutePath().toString(),
                path.toAbsolutePath().toString(),
                status,
                "2026-01-01",
                "2026-03-31",
                "quarterly",
                fingerprint.fileSize(),
                fingerprint.mtimeEpochMs(),
                fingerprint.first1MbSha256(),
                fingerprint.last1MbSha256(),
                fingerprint.fileFingerprint(),
                "not-scanned",
                "not-indexed",
                now,
                now,
                null,
                null,
                null
        );
    }

    private Path createTempPst(String name, String content) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    private record TestContext(CatalogRepository repository, ShardManager shardManager) {
    }
}
