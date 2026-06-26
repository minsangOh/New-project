package com.example.pstarchive.cli;

import com.example.pstarchive.model.PstFileRecord;
import com.example.pstarchive.util.FileSizeUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "stats", description = "Show archive catalog statistics.")
public class StatsCommand implements Callable<Integer> {
    @ParentCommand
    private ArchiveCommand archive;

    @Override
    public Integer call() throws Exception {
        archive.database().initialize();
        List<PstFileRecord> records = archive.repository().listPsts();
        long totalBytes = records.stream().mapToLong(PstFileRecord::sizeBytes).sum();
        System.out.println("Registered PST files: " + records.size());
        System.out.println("Total PST bytes: " + FileSizeUtils.humanReadable(totalBytes));
        Map<String, Integer> counts = archive.repository().countByStatus();
        if (!counts.isEmpty()) {
            System.out.println("By status:");
            counts.forEach((status, count) -> System.out.println("  " + status + ": " + count));
        }
        return 0;
    }
}
