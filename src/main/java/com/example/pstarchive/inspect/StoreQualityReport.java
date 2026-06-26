package com.example.pstarchive.inspect;

import java.util.List;

public record StoreQualityReport(
        StoreQualityDecision decision,
        StoreInspectionResult inspection,
        double bodyTextCoveragePercent,
        List<String> reasons,
        List<String> warnings,
        List<String> blockingIssues,
        List<String> topErrors
) {
}
