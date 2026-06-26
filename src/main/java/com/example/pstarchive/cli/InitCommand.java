package com.example.pstarchive.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

@Command(name = "init", description = "Create the archive catalog database and base directories.")
public class InitCommand implements Callable<Integer> {
    @ParentCommand
    private ArchiveCommand archive;

    @Override
    public Integer call() throws Exception {
        archive.database().initialize();
        System.out.println("Archive catalog initialized.");
        System.out.println("Data directory: " + archive.dataDir().toAbsolutePath());
        return 0;
    }
}
