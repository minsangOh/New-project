package com.example.pstarchive.verify;

import com.example.pstarchive.model.PstStatus;

public record VerifyResult(
        String pstId,
        String path,
        boolean exists,
        boolean fingerprintMatches,
        PstStatus resultingStatus,
        String message
) {
}
