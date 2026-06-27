package com.example.pstarchive.textquality;

public record HtmlTextComparison(
        TextQualityResult bodyHtmlQuality,
        TextQualityResult bodyHtmlTextQuality,
        String detectedCharset,
        HtmlTextDamageType damageType,
        String reason
) {
}
