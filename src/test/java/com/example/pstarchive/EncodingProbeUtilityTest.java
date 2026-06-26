package com.example.pstarchive;

import com.example.pstarchive.cli.ArchiveCommand;
import com.example.pstarchive.encoding.BestEffortTextDecoder;
import com.example.pstarchive.encoding.CharsetAliasResolver;
import com.example.pstarchive.encoding.DecodedTextCandidate;
import com.example.pstarchive.encoding.EncodingProbeResult;
import com.example.pstarchive.encoding.HtmlCharsetDetector;
import com.example.pstarchive.encoding.KoreanTextQualityScorer;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EncodingProbeUtilityTest {
    private static final String SAMSUNG = "\uC0BC\uC131\uC804\uC790";
    private static final String WATER_PURIFIER = "\uC5BC\uC74C\uC815\uC218\uAE30";
    private static final String TAPING = "\uD14C\uC774\uD551";
    private static final String JOINT = "\uC870\uC778\uD2B8\uBD80";
    private static final String WIRE_BREAK = "\uB2E8\uC120";
    private static final String USER_MOJIBAKE_SAMPLE = "?\uC88E\uB8DE?\uC1FF\uB71D?\uC208\uC095";

    private final BestEffortTextDecoder decoder = new BestEffortTextDecoder();

    @Test
    void mapsKsC5601AliasToKoreanFallbacks() {
        List<String> names = CharsetAliasResolver.candidatesFor("ks_c_5601-1987").stream()
                .map(CharsetAliasResolver::canonicalLabel)
                .toList();

        assertTrue(names.contains("MS949"));
        assertTrue(names.contains("EUC-KR"));
    }

    @Test
    void decodesUtf8KoreanBytes() {
        String expected = SAMSUNG + " " + WATER_PURIFIER;
        byte[] bytes = expected.getBytes(StandardCharsets.UTF_8);

        EncodingProbeResult result = decoder.probe(USER_MOJIBAKE_SAMPLE, bytes, "utf-8");

        assertEquals(expected, result.decodedBestText());
        assertEquals("OK", result.decodeStatus());
    }

    @Test
    void decodesMs949KoreanBytes() {
        String expected = TAPING + " " + JOINT + " " + WIRE_BREAK;
        byte[] bytes = expected.getBytes(Charset.forName("MS949"));

        EncodingProbeResult result = decoder.probe(USER_MOJIBAKE_SAMPLE, bytes, "ks_c_5601-1987");

        assertEquals(expected, result.decodedBestText());
        assertEquals("OK", result.decodeStatus());
    }

    @Test
    void decodesEucKrKoreanBytes() {
        byte[] bytes = SAMSUNG.getBytes(Charset.forName("EUC-KR"));

        EncodingProbeResult result = decoder.probe("???", bytes, "EUC-KR");

        assertEquals(SAMSUNG, result.decodedBestText());
        assertEquals("OK", result.decodeStatus());
    }

    @Test
    void detectsBrokenMojibakeRatio() {
        double broken = KoreanTextQualityScorer.brokenCharRatio(USER_MOJIBAKE_SAMPLE);
        double normal = KoreanTextQualityScorer.brokenCharRatio(SAMSUNG + " " + WATER_PURIFIER);

        assertTrue(broken > 0.50);
        assertTrue(normal < 0.01);
        assertTrue(broken > normal);
    }

    @Test
    void normalKoreanQuestionMarkIsNotTreatedAsBroken() {
        assertTrue(KoreanTextQualityScorer.brokenCharRatio("\uC9C4\uC9DC?") < 0.01);
    }

    @Test
    void choosesCandidateWithLowerBrokenRatioAndKoreanText() {
        DecodedTextCandidate best = decoder.chooseBestDecodedText(List.of(
                new DecodedTextCandidate("broken", USER_MOJIBAKE_SAMPLE, 0.75, 0.10, true),
                new DecodedTextCandidate("MS949", SAMSUNG, 0.0, 1.0, true)
        ));

        assertNotNull(best);
        assertEquals("MS949", best.label());
    }

    @Test
    void detectsHtmlMetaCharset() {
        String html = """
                <html><head><meta http-equiv="Content-Type" content="text/html; charset=ks_c_5601-1987"></head></html>
                """;

        assertEquals("ks_c_5601-1987", HtmlCharsetDetector.detect(html).orElseThrow());
    }

    @Test
    void marksAlreadyBrokenStringWithoutRawBytesAsUnrecoverable() {
        EncodingProbeResult result = decoder.probe(USER_MOJIBAKE_SAMPLE, null, "ks_c_5601-1987");

        assertEquals("UNRECOVERABLE", result.decodeStatus());
    }

    @Test
    void archiveCommandExposesEncodingProbe() {
        CommandLine commandLine = new CommandLine(new ArchiveCommand());

        assertTrue(commandLine.getSubcommands().containsKey("encoding-probe"));
        assertDoesNotThrow(() -> commandLine.parseArgs("encoding-probe", "sample.pst", "--limit", "10"));
        assertDoesNotThrow(() -> commandLine.parseArgs("encoding-probe", "sample.pst", "--limit", "10",
                "--output", "probe-report.txt"));
    }
}
