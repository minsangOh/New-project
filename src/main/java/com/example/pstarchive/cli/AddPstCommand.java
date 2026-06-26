package com.example.pstarchive.cli;

import com.example.pstarchive.catalog.CatalogRepository;
import com.example.pstarchive.model.PstFileRecord;
import com.example.pstarchive.model.PstFingerprint;
import com.example.pstarchive.model.PstStatus;
import com.example.pstarchive.util.TimeUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;

@Command(name = "add-pst", description = "Register a PST file and create its shard directory.")
public class AddPstCommand implements Callable<Integer> {
    @ParentCommand
    private ArchiveCommand archive;

    @Parameters(index = "0", description = "PST file path.")
    private Path pstPath;

    @Option(names = "--status", description = "Initial status: active, warm, cold, archive. Default: ${DEFAULT-VALUE}")
    private String status = "archive";

    @Option(names = "--period-from", description = "Archive period start date, for example 2026-04-01.")
    private String periodFrom;

    @Option(names = "--period-to", description = "Archive period end date, for example 2026-06-30.")
    private String periodTo;

    @Option(names = "--split-strategy", description = "Split strategy label. Default: quarterly")
    private String splitStrategy = "quarterly";

    @Override
    public Integer call() throws Exception {
        archive.database().initialize();
        Path absolutePath = pstPath.toAbsolutePath().normalize();
        PstStatus parsedStatus = PstStatus.fromValue(status);
        PstFingerprint fingerprint = archive.fingerprintService().calculate(absolutePath);

        CatalogRepository repository = archive.repository();
        if (repository.findByFingerprint(fingerprint.fileFingerprint()).isPresent()) {
            System.err.println("This PST already appears to be registered. Fingerprint: " + fingerprint.fileFingerprint());
            return 2;
        }

        String now = TimeUtils.nowIso();
        String pstId = UUID.randomUUID().toString();
        PstFileRecord record = new PstFileRecord(
                pstId,
                absolutePath.getFileName().toString(),
                absolutePath.toString(),
                absolutePath.toString(),
                parsedStatus,
                periodFrom,
                periodTo,
                splitStrategy,
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

        repository.insertPst(record);
        archive.shardManager().createShard(record);
        System.out.println("PST registered.");
        System.out.println("PST ID: " + pstId);
        System.out.println("Status: " + parsedStatus.value().toLowerCase(Locale.ROOT));
        System.out.println("Shard: " + archive.dataDir().resolve("shards").resolve(pstId).toAbsolutePath());
        return 0;
    }
}
