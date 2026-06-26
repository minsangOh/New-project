package com.example.pstarchive.encoding;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CharsetAliasResolver {
    private CharsetAliasResolver() {
    }

    public static List<Charset> candidatesFor(String charsetName) {
        Set<Charset> charsets = new LinkedHashSet<>();
        String normalized = charsetName == null ? "" : charsetName.trim().toLowerCase(Locale.ROOT);

        if (normalized.equals("ks_c_5601-1987")
                || normalized.equals("ks_c_5601")
                || normalized.equals("ks-c-5601-1987")
                || normalized.equals("euc-kr")
                || normalized.equals("x-windows-949")
                || normalized.equals("ms949")
                || normalized.equals("windows-949")) {
            addIfSupported(charsets, "MS949");
            addIfSupported(charsets, "EUC-KR");
        }

        if (normalized.equals("utf-8") || normalized.equals("utf8")) {
            charsets.add(StandardCharsets.UTF_8);
        }

        charsets.add(StandardCharsets.UTF_8);
        addIfSupported(charsets, "MS949");
        addIfSupported(charsets, "EUC-KR");
        return new ArrayList<>(charsets);
    }

    public static String canonicalLabel(Charset charset) {
        if (charset == null) {
            return "<unknown>";
        }
        String name = charset.name();
        if (name.equalsIgnoreCase("x-windows-949")) {
            return "MS949";
        }
        return name;
    }

    private static void addIfSupported(Set<Charset> charsets, String name) {
        if (Charset.isSupported(name)) {
            charsets.add(Charset.forName(name));
        }
    }
}
