package com.example.pstarchive.textquality;

import java.util.List;

public record TextQualityReport(
        long messagesChecked,
        long fieldsChecked,
        long okFields,
        long suspectFields,
        long degradedFields,
        long brokenFields,
        long nullFields,
        long nulCharFields,
        long questionHeavyFields,
        long mojibakeFields,
        long statusMismatchCount,
        List<TextFieldDiagnostic> mismatchExamples
) {
}
