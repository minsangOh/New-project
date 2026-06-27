package com.example.pstarchive.cli;

import com.example.pstarchive.catalog.CatalogDatabase;
import com.example.pstarchive.catalog.CatalogRepository;
import com.example.pstarchive.fingerprint.PstFingerprintService;
import com.example.pstarchive.shard.ShardManager;
import com.example.pstarchive.shard.ShardPathResolver;
import com.example.pstarchive.util.AppPaths;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;

@Command(
        name = "archive",
        mixinStandardHelpOptions = true,
        version = "pst-archive-search 0.1.0",
        description = "Local PST archive catalog manager.",
        subcommands = {
                InitCommand.class,
                AddPstCommand.class,
                ListCommand.class,
                ShowCommand.class,
                MarkStatusCommand.MarkActive.class,
                MarkStatusCommand.MarkArchive.class,
                MarkStatusCommand.MarkWarm.class,
                MarkStatusCommand.MarkCold.class,
                VerifyCommand.class,
                MovePstCommand.class,
                StatsCommand.class,
                ScanFileCommand.class,
                ScanPstCommand.class,
                EncodingProbeCommand.class,
                IndexFileCommand.class,
                IndexPstCommand.class,
                InspectStoreCommand.class,
                SampleMessagesCommand.class,
                ShowMessageCommand.class,
                QualityReportCommand.class,
                SearchStoreCommand.class,
                DiagnoseTextQualityCommand.class,
                DumpMessageRawCommand.class
        }
)
public class ArchiveCommand {
    @Option(names = "--data-dir", description = "Data directory. Default: ${DEFAULT-VALUE}")
    private Path dataDir = AppPaths.defaultDataDir();

    public Path dataDir() {
        return dataDir;
    }

    public CatalogDatabase database() {
        return new CatalogDatabase(dataDir);
    }

    public CatalogRepository repository() {
        return new CatalogRepository(database());
    }

    public ShardManager shardManager() {
        return new ShardManager(new ShardPathResolver(dataDir));
    }

    public PstFingerprintService fingerprintService() {
        return new PstFingerprintService();
    }
}
