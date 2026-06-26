package com.example.pstarchive.cli;

import com.example.pstarchive.pst.JavaLibPstScanner;
import com.example.pstarchive.pst.PstScanOptions;
import com.example.pstarchive.pst.PstScanService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "scan-file",
        mixinStandardHelpOptions = true,
        description = "Open a PST file directly and print a diagnostic folder/mail preview."
)
public class ScanFileCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "PST file path.")
    private Path pstPath;

    @Option(names = "--limit", description = "Maximum number of mail previews to print. Default: ${DEFAULT-VALUE}")
    private int limit = 10;

    @Override
    public Integer call() {
        PstScanService service = new PstScanService(new JavaLibPstScanner());
        var summary = service.scan(pstPath, new PstScanOptions(limit, "scan-file", System.out));
        return summary.fatalErrors() == 0 ? 0 : 1;
    }
}
