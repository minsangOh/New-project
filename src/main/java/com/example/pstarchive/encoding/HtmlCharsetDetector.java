package com.example.pstarchive.encoding;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HtmlCharsetDetector {
    private static final Pattern META_CHARSET = Pattern.compile(
            "(?i)<meta\\s+[^>]*charset\\s*=\\s*['\"]?([^\\s'\";>]+)");
    private static final Pattern CONTENT_TYPE_CHARSET = Pattern.compile(
            "(?i)content\\s*=\\s*['\"][^'\"]*charset\\s*=\\s*([^\\s'\";>]+)");

    private HtmlCharsetDetector() {
    }

    public static Optional<String> detect(String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        Matcher direct = META_CHARSET.matcher(html);
        if (direct.find()) {
            return Optional.of(clean(direct.group(1)));
        }
        Matcher contentType = CONTENT_TYPE_CHARSET.matcher(html);
        if (contentType.find()) {
            return Optional.of(clean(contentType.group(1)));
        }
        return Optional.empty();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replace("\"", "").replace("'", "");
    }
}
