package com.example.pstarchive.cli;

import com.example.pstarchive.model.PstFileRecord;
import com.example.pstarchive.model.PstFingerprint;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

@Command(name = "move-pst", description = "Reconnect a registered PST to a new path.")
public class MovePstCommand implements Callable<Integer> {
    @ParentCommand
    private ArchiveCommand archive;

    @Parameters(index = "0", description = "PST ID.")
    private String pstId;

    @Parameters(index = "1", description = "New PST path.")
    private Path newPath;

    @Override
    public Integer call() throws Exception {
        archive.database().initialize();
        Optional<PstFileRecord> maybeRecord = archive.repository().findById(pstId);
        if (maybeRecord.isEmpty()) {
            System.err.println("PST not found: " + pstId);
            return 2;
        }

        Path absolutePath = newPath.toAbsolutePath().normalize();
        PstFingerprint fingerprint = archive.fingerprintService().calculate(absolutePath);
        PstFileRecord record = maybeRecord.get();
        if (!record.fileFingerprint().equals(fingerprint.fileFingerprint())) {
            System.err.println("New path does not match the stored PST fingerprint.");
            System.err.println("Stored: " + record.fileFingerprint());
            System.err.println("New:    " + fingerprint.fileFingerprint());
            return 3;
        }

        archive.repository().updatePath(pstId, absolutePath.toString(), fingerprint);
        PstFileRecord updated = archive.repository().findById(pstId).orElseThrow();
        archive.shardManager().writeManifest(updated);
        System.out.println("PST path updated.");
        System.out.println("PST ID: " + pstId);
        System.out.println("New path: " + absolutePath);
        return 0;
    }
}
