package com.example.pstarchive;

import com.example.pstarchive.cli.ArchiveCommand;
import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new ArchiveCommand()).execute(args);
        System.exit(exitCode);
    }
}
