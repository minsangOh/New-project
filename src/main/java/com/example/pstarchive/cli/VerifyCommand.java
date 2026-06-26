package com.example.pstarchive.cli;

import com.example.pstarchive.fingerprint.PstFingerprintService;
import com.example.pstarchive.model.PstFileRecord;
import com.example.pstarchive.verify.ArchiveVerifier;
import com.example.pstarchive.verify.VerifyResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

@Command(name = "verify", description = "Verify registered PST paths and fingerprints.")
public class VerifyCommand implements Callable<Integer> {
    @ParentCommand
    private ArchiveCommand archive;

    @Option(names = "--pst", description = "Verify only one PST ID.")
    private String pstId;

    @Override
    public Integer call() throws Exception {
        archive.database().initialize();
        ArchiveVerifier verifier = new ArchiveVerifier(archive.repository(), new PstFingerprintService());
        if (pstId != null) {
            Optional<PstFileRecord> record = archive.repository().findById(pstId);
            if (record.isEmpty()) {
                System.err.println("PST not found: " + pstId);
                return 2;
            }
            print(verifier.verify(record.get()));
            return 0;
        }

        List<VerifyResult> results = verifier.verifyAll();
        if (results.isEmpty()) {
            System.out.println("No PST files registered.");
            return 0;
        }
        results.forEach(this::print);
        return 0;
    }

    private void print(VerifyResult result) {
        System.out.printf("%s  %s  exists=%s  fingerprint=%s  %s%n",
                result.pstId(),
                result.resultingStatus().value(),
                result.exists(),
                result.fingerprintMatches() ? "ok" : "changed",
                result.message());
    }
}
