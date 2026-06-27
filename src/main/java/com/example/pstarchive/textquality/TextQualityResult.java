package com.example.pstarchive.textquality;

import java.util.List;

public record TextQualityResult(
        TextQualityLevel level,
        String reason,
        int visibleChars,
        int nulCharCount,
        int questionMarkCount,
        double questionMarkRatio,
        int repeatedQuestionRuns,
        int mojibakePatternCount,
        List<String> warnings,
        String sanitizedPreview
) {
    public boolean hasWarning(String warning) {
        return warnings.contains(warning);
    }
}
