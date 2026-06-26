package com.example.pstarchive.cli;

import com.example.pstarchive.fingerprint.PstFingerprintService;
import com.example.pstarchive.index.PstIndexOptions;
import com.example.pstarchive.index.PstIndexService;
import com.example.pstarchive.index.PstIndexSummary;
import com.example.pstarchive.model.PstFileRecord;
import com.example.pstarchive.model.PstFingerprint;
import com.example.pstarchive.shard.ShardPathResolver;
import com.example.pstarchive.util.FileSizeUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

@Command(
        name = "index-pst",
        mixinStandardHelpOptions = true,
        description = "Extract a catalog-registered PST into its per-PST SQLite shard store. Search is not implemented in this phase."
)
public class IndexPstCommand implements Callable<Integer> {
    @ParentCommand
    private ArchiveCommand archive;

    @Parameters(index = "0", description = "PST ID.")
    private String pstId;

    @Option(names = "--limit", description = "Maximum number of messages to store. Default: ${DEFAULT-VALUE}")
    private int limit = 1000;

    @Option(names = "--replace", description = "Delete existing folders, messages, and index_errors before indexing.")
    private boolean replace;

    @Override
    public Integer call() throws Exception {
        archive.database().initialize();
        Optional<PstFileRecord> maybeRecord = archive.repository().findById(pstId);
        if (maybeRecord.isEmpty()) {
            System.err.println("PST not found: " + pstId);
            return 2;
        }

        PstFileRecord record = maybeRecord.get();
        archive.shardManager().createShard(record);
        Path pstPath = Path.of(record.currentPath()).toAbsolutePath().normalize();
        Path storePath = new ShardPathResolver(archive.dataDir()).storeDatabase(record.pstId()).toAbsolutePath().normalize();

        System.out.println("Catalog PST ID: " + record.pstId());
        System.out.println("Catalog status: " + record.status().value());
        System.out.println("Catalog path: " + record.currentPath());
        System.out.println("Shard store: " + storePath);

        if (!Files.exists(pstPath)) {
            System.err.println("Current file exists: false");
            return 2;
        }
        PstFingerprint current = new PstFingerprintService().calculate(pstPath);
        System.out.println("Current size: " + FileSizeUtils.humanReadable(current.fileSize()));
        System.out.println("Current modified epoch ms: " + current.mtimeEpochMs());
        System.out.println("Fingerprint matches catalog: " + current.fileFingerprint().equals(record.fileFingerprint()));
        System.out.println();

        PstIndexSummary summary = new PstIndexService().index(
                new PstIndexOptions(pstPath, storePath, limit, replace, System.out)
        );
        return "FAILED".equals(summary.status()) ? 1 : 0;
    }
}
