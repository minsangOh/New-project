package com.example.pstarchive.cli;

import com.example.pstarchive.model.PstFileRecord;
import com.example.pstarchive.util.FileSizeUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "list", description = "List registered PST files.")
public class ListCommand implements Callable<Integer> {
    @ParentCommand
    private ArchiveCommand archive;

    @Override
    public Integer call() throws Exception {
        archive.database().initialize();
        List<PstFileRecord> records = archive.repository().listPsts();
        if (records.isEmpty()) {
            System.out.println("No PST files registered.");
            return 0;
        }
        System.out.printf("%-36s  %-8s  %-12s  %s%n", "PST ID", "STATUS", "SIZE", "PATH");
        for (PstFileRecord record : records) {
            System.out.printf("%-36s  %-8s  %-12s  %s%n",
                    record.pstId(),
                    record.status().value(),
                    FileSizeUtils.humanReadable(record.sizeBytes()),
                    record.currentPath());
        }
        return 0;
    }
}
