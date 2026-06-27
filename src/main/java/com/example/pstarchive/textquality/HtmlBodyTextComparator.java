package com.example.pstarchive.textquality;

import com.example.pstarchive.encoding.HtmlCharsetDetector;

public class HtmlBodyTextComparator {
    private final TextQualityAnalyzer analyzer = new TextQualityAnalyzer();

    public HtmlTextComparison compare(String bodyHtml, String bodyHtmlText) {
        TextQualityResult htmlQuality = analyzer.diagnose(bodyHtml);
        TextQualityResult textQuality = analyzer.diagnose(bodyHtmlText);
        String charset = HtmlCharsetDetector.detect(bodyHtml).orElse("<not_detected>");
        HtmlTextDamageType damageType = damageType(htmlQuality, textQuality);
        return new HtmlTextComparison(htmlQuality, textQuality, charset, damageType, reason(damageType));
    }

    private HtmlTextDamageType damageType(TextQualityResult htmlQuality, TextQualityResult textQuality) {
        boolean htmlMissing = htmlQuality.level() == TextQualityLevel.NULL;
        boolean htmlBroken = isBrokenish(htmlQuality.level());
        boolean textBroken = isBrokenish(textQuality.level());
        if (htmlMissing && textBroken) {
            return HtmlTextDamageType.GETTER_OR_RECOVERY_PROBLEM;
        }
        if (htmlBroken && textBroken) {
            return HtmlTextDamageType.SOURCE_OR_DECODING_BROKEN;
        }
        if (htmlBroken) {
            return HtmlTextDamageType.SOURCE_HTML_BROKEN;
        }
        if (!htmlBroken && textBroken) {
            return HtmlTextDamageType.HTML_TO_TEXT_EXTRACTION_PROBLEM;
        }
        return HtmlTextDamageType.OK;
    }

    private boolean isBrokenish(TextQualityLevel level) {
        return level == TextQualityLevel.DEGRADED || level == TextQualityLevel.BROKEN;
    }

    private String reason(HtmlTextDamageType damageType) {
        return switch (damageType) {
            case OK -> "body_html_and_body_html_text_look_usable";
            case SOURCE_HTML_BROKEN -> "body_html_has_broken_text_signals";
            case HTML_TO_TEXT_EXTRACTION_PROBLEM -> "body_html_looks_better_than_body_html_text";
            case GETTER_OR_RECOVERY_PROBLEM -> "body_html_missing_but_body_html_text_is_broken";
            case SOURCE_OR_DECODING_BROKEN -> "body_html_and_body_html_text_are_both_broken";
        };
    }
}
