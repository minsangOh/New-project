package com.example.pstarchive.encoding;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BestEffortTextDecoder {
    public EncodingProbeResult probe(String getterText, byte[] rawBytes, String charsetHint) {
        List<DecodedTextCandidate> candidates = new ArrayList<>();
        if (rawBytes != null && rawBytes.length > 0) {
            for (Charset charset : CharsetAliasResolver.candidatesFor(charsetHint)) {
                decodeRaw(rawBytes, charset)
                        .ifPresent(text -> candidates.add(candidate(CharsetAliasResolver.canonicalLabel(charset), text, true)));
            }
        }

        if (getterText != null && !getterText.isEmpty()) {
            candidates.add(candidate("getter", getterText, false));
            candidates.addAll(redecodeGetterCandidates(getterText));
        }

        DecodedTextCandidate best = chooseBestDecodedText(candidates);
        double before = KoreanTextQualityScorer.brokenCharRatio(getterText);
        if (best == null) {
            return new EncodingProbeResult(
                    getterText,
                    "<unrecoverable>",
                    "UNRECOVERABLE",
                    "<none>",
                    before,
                    before,
                    KoreanTextQualityScorer.hangulRatio(getterText),
                    rawBytes != null && rawBytes.length > 0,
                    candidates
            );
        }

        double after = best.brokenCharRatio();
        String status = status(getterText, best, rawBytes);
        return new EncodingProbeResult(
                getterText,
                best.text(),
                status,
                best.label(),
                before,
                after,
                best.hangulRatio(),
                rawBytes != null && rawBytes.length > 0,
                candidates
        );
    }

    public DecodedTextCandidate chooseBestDecodedText(List<DecodedTextCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .filter(candidate -> candidate.text() != null)
                .min(Comparator
                        .comparingDouble(DecodedTextCandidate::brokenCharRatio)
                        .thenComparing(Comparator.comparingDouble(DecodedTextCandidate::hangulRatio).reversed())
                        .thenComparing(candidate -> candidate.rawBytesAvailable() ? 0 : 1)
                        .thenComparingInt(candidate -> candidate.text().length() == 0 ? 1 : 0))
                .orElse(null);
    }

    private List<DecodedTextCandidate> redecodeGetterCandidates(String getterText) {
        List<DecodedTextCandidate> candidates = new ArrayList<>();
        for (Charset sourceCharset : CharsetAliasResolver.candidatesFor("ks_c_5601-1987")) {
            byte[] bytes = getterText.getBytes(sourceCharset);
            decodeRaw(bytes, StandardCharsets.UTF_8)
                    .ifPresent(text -> candidates.add(candidate("getter-as-" + CharsetAliasResolver.canonicalLabel(sourceCharset) + "-to-UTF-8", text, false)));
        }
        byte[] latin1 = getterText.getBytes(StandardCharsets.ISO_8859_1);
        for (Charset target : CharsetAliasResolver.candidatesFor(null)) {
            decodeRaw(latin1, target)
                    .ifPresent(text -> candidates.add(candidate("getter-as-ISO-8859-1-to-" + CharsetAliasResolver.canonicalLabel(target), text, false)));
        }
        return candidates;
    }

    private java.util.Optional<String> decodeRaw(byte[] bytes, Charset charset) {
        try {
            CharBuffer decoded = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return java.util.Optional.of(decoded.toString().trim());
        } catch (CharacterCodingException e) {
            return java.util.Optional.empty();
        }
    }

    private DecodedTextCandidate candidate(String label, String text, boolean rawBytesAvailable) {
        return new DecodedTextCandidate(
                label,
                text,
                KoreanTextQualityScorer.brokenCharRatio(text),
                KoreanTextQualityScorer.hangulRatio(text),
                rawBytesAvailable
        );
    }

    private String status(String getterText, DecodedTextCandidate best, byte[] rawBytes) {
        double before = KoreanTextQualityScorer.brokenCharRatio(getterText);
        if (rawBytes == null || rawBytes.length == 0) {
            if (before > 0.08 && best.brokenCharRatio() >= before) {
                return "UNRECOVERABLE";
            }
            return best.brokenCharRatio() <= before ? "DEGRADED" : "UNRECOVERABLE";
        }
        if (best.brokenCharRatio() < before || best.hangulRatio() > KoreanTextQualityScorer.hangulRatio(getterText)) {
            return "OK";
        }
        return before > 0.08 ? "DEGRADED" : "OK";
    }
}
