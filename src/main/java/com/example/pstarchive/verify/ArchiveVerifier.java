package com.example.pstarchive.verify;

import com.example.pstarchive.catalog.CatalogRepository;
import com.example.pstarchive.fingerprint.PstFingerprintService;
import com.example.pstarchive.model.PstFileRecord;
import com.example.pstarchive.model.PstFingerprint;
import com.example.pstarchive.model.PstStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ArchiveVerifier {
    private final CatalogRepository repository;
    private final PstFingerprintService fingerprintService;

    public ArchiveVerifier(CatalogRepository repository, PstFingerprintService fingerprintService) {
        this.repository = repository;
        this.fingerprintService = fingerprintService;
    }

    public List<VerifyResult> verifyAll() throws SQLException {
        List<VerifyResult> results = new ArrayList<>();
        for (PstFileRecord record : repository.listPsts()) {
            results.add(verify(record));
        }
        return results;
    }

    public VerifyResult verify(PstFileRecord record) throws SQLException {
        Path path = Path.of(record.currentPath());
        if (!Files.exists(path)) {
            repository.markVerified(record.pstId(), PstStatus.MISSING, false, null, null);
            return new VerifyResult(record.pstId(), record.currentPath(), false, false, PstStatus.MISSING,
                    "File is missing.");
        }

        try {
            PstFingerprint current = fingerprintService.calculate(path);
            boolean matches = current.fileFingerprint().equals(record.fileFingerprint());
            PstStatus resultingStatus = matches ? record.status() : PstStatus.INVALID;
            repository.markVerified(record.pstId(), resultingStatus, true, current.fileSize(), current.mtimeEpochMs());
            String message = matches ? "Fingerprint matches." : "Fingerprint changed; index should be checked before use.";
            return new VerifyResult(record.pstId(), record.currentPath(), true, matches, resultingStatus, message);
        } catch (IOException e) {
            repository.markVerified(record.pstId(), PstStatus.INVALID, true, null, null);
            return new VerifyResult(record.pstId(), record.currentPath(), true, false, PstStatus.INVALID,
                    "Fingerprint calculation failed: " + e.getMessage());
        }
    }
}
