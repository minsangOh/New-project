package com.example.pstarchive.cli;

import com.example.pstarchive.model.PstFileRecord;
import com.example.pstarchive.model.PstStatus;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.Optional;
import java.util.concurrent.Callable;

public abstract class MarkStatusCommand implements Callable<Integer> {
    @ParentCommand
    protected ArchiveCommand archive;

    @Parameters(index = "0", description = "PST ID.")
    protected String pstId;

    protected abstract PstStatus targetStatus();

    @Override
    public Integer call() throws Exception {
        archive.database().initialize();
        Optional<PstFileRecord> record = archive.repository().findById(pstId);
        if (record.isEmpty()) {
            System.err.println("PST not found: " + pstId);
            return 2;
        }
        archive.repository().updateStatus(pstId, targetStatus());
        PstFileRecord updated = archive.repository().findById(pstId).orElseThrow();
        archive.shardManager().writeManifest(updated);
        System.out.println("PST status updated: " + pstId + " -> " + targetStatus().value());
        return 0;
    }

    @Command(name = "mark-active", description = "Mark a PST as active.")
    public static class MarkActive extends MarkStatusCommand {
        @Override
        protected PstStatus targetStatus() {
            return PstStatus.ACTIVE;
        }
    }

    @Command(name = "mark-archive", description = "Mark a PST as archive.")
    public static class MarkArchive extends MarkStatusCommand {
        @Override
        protected PstStatus targetStatus() {
            return PstStatus.ARCHIVE;
        }
    }

    @Command(name = "mark-warm", description = "Mark a PST as warm.")
    public static class MarkWarm extends MarkStatusCommand {
        @Override
        protected PstStatus targetStatus() {
            return PstStatus.WARM;
        }
    }

    @Command(name = "mark-cold", description = "Mark a PST as cold.")
    public static class MarkCold extends MarkStatusCommand {
        @Override
        protected PstStatus targetStatus() {
            return PstStatus.COLD;
        }
    }
}
