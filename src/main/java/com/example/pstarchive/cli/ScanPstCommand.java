package com.example.pstarchive.cli;

import com.example.pstarchive.fingerprint.PstFingerprintService;
import com.example.pstarchive.model.PstFileRecord;
import com.example.pstarchive.model.PstFingerprint;
import com.example.pstarchive.pst.JavaLibPstScanner;
import com.example.pstarchive.pst.PstScanOptions;
import com.example.pstarchive.pst.PstScanService;
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
        name = "scan-pst",
        mixinStandardHelpOptions = true,
        description = "Open a catalog-registered PST and print a diagnostic folder/mail preview."
)
public class ScanPstCommand implements Callable<Integer> {
    @ParentCommand
    private ArchiveCommand archive;

    @Parameters(index = "0", description = "PST ID.")
    private String pstId;

    @Option(names = "--limit", description = "Maximum number of mail previews to print. Default: ${DEFAULT-VALUE}")
    private int limit = 10;

    @Override
    public Integer call() throws Exception {
        archive.database().initialize();
        Optional<PstFileRecord> maybeRecord = archive.repository().findById(pstId);
        if (maybeRecord.isEmpty()) {
            System.err.println("PST not found: " + pstId);
            return 2;
        }

        PstFileRecord record = maybeRecord.get();
        Path path = Path.of(record.currentPath()).toAbsolutePath().normalize();
        System.out.println("Catalog PST ID: " + record.pstId());
        System.out.println("Catalog status: " + record.status().value());
        System.out.println("Catalog path: " + record.currentPath());

        if (Files.exists(path)) {
            PstFingerprint current = new PstFingerprintService().calculate(path);
            System.out.println("Current size: " + FileSizeUtils.humanReadable(current.fileSize()));
            System.out.println("Current modified epoch ms: " + current.mtimeEpochMs());
            System.out.println("Fingerprint matches catalog: " + current.fileFingerprint().equals(record.fileFingerprint()));
        } else {
            System.out.println("Current file exists: false");
        }
        System.out.println();

        PstScanService service = new PstScanService(new JavaLibPstScanner());
        var summary = service.scan(path, new PstScanOptions(limit, "scan-pst", System.out));
        return summary.fatalErrors() == 0 ? 0 : 1;
    }
}
