package com.example.pstarchive.util;

import org.jsoup.Jsoup;

public final class HtmlTextExtractor {
    private HtmlTextExtractor() {
    }

    public static String toText(String html) {
        if (html == null) {
            return null;
        }
        if (html.isEmpty()) {
            return "";
        }
        return Jsoup.parse(html).text();
    }
}
