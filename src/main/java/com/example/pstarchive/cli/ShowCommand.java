package com.example.pstarchive.cli;

import com.example.pstarchive.model.PstFileRecord;
import com.example.pstarchive.util.FileSizeUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.Optional;
import java.util.concurrent.Callable;

@Command(name = "show", description = "Show one PST catalog record.")
public class ShowCommand implements Callable<Integer> {
    @ParentCommand
    private ArchiveCommand archive;

    @Parameters(index = "0", description = "PST ID.")
    private String pstId;

    @Override
    public Integer call() throws Exception {
        archive.database().initialize();
        Optional<PstFileRecord> maybeRecord = archive.repository().findById(pstId);
        if (maybeRecord.isEmpty()) {
            System.err.println("PST not found: " + pstId);
            return 2;
        }
        PstFileRecord record = maybeRecord.get();
        System.out.println("PST ID: " + record.pstId());
        System.out.println("Display name: " + record.displayName());
        System.out.println("Status: " + record.status().value());
        System.out.println("Current path: " + record.currentPath());
        System.out.println("Original path: " + record.originalPath());
        System.out.println("Period: " + nullToDash(record.periodFrom()) + " to " + nullToDash(record.periodTo()));
        System.out.println("Split strategy: " + nullToDash(record.splitStrategy()));
        System.out.println("Size: " + FileSizeUtils.humanReadable(record.sizeBytes()));
        System.out.println("Fingerprint: " + record.fileFingerprint());
        System.out.println("Last verified: " + nullToDash(record.lastVerifiedAt()));
        return 0;
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
