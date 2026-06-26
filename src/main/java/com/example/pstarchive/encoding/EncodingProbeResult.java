package com.example.pstarchive.encoding;

import java.util.List;

public record EncodingProbeResult(
        String rawGetterText,
        String decodedBestText,
        String decodeStatus,
        String bestLabel,
        double brokenCharRatioBefore,
        double brokenCharRatioAfter,
        double hangulRatioAfter,
        boolean rawBytesAvailable,
        List<DecodedTextCandidate> candidates
) {
}
