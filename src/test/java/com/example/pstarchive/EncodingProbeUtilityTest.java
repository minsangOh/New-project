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
        byte[] bytes = "삼성전자 얼음정수기".getBytes(StandardCharsets.UTF_8);

        EncodingProbeResult result = decoder.probe("占쏙옙", bytes, "utf-8");

        assertEquals("삼성전자 얼음정수기", result.decodedBestText());
        assertEquals("OK", result.decodeStatus());
    }

    @Test
    void decodesMs949KoreanBytes() {
        byte[] bytes = "테이핑 조인트부 단선".getBytes(Charset.forName("MS949"));

        EncodingProbeResult result = decoder.probe("?좎룞", bytes, "ks_c_5601-1987");

        assertEquals("테이핑 조인트부 단선", result.decodedBestText());
        assertEquals("OK", result.decodeStatus());
    }

    @Test
    void decodesEucKrKoreanBytes() {
        byte[] bytes = "삼성전자".getBytes(Charset.forName("EUC-KR"));

        EncodingProbeResult result = decoder.probe("???", bytes, "EUC-KR");

        assertEquals("삼성전자", result.decodedBestText());
        assertEquals("OK", result.decodeStatus());
    }

    @Test
    void detectsBrokenMojibakeRatio() {
        double broken = KoreanTextQualityScorer.brokenCharRatio("?좎룞?쇿뜝?숈삕");
        double normal = KoreanTextQualityScorer.brokenCharRatio("삼성전자 얼음정수기");

        assertTrue(broken > normal);
    }

    @Test
    void choosesCandidateWithLowerBrokenRatioAndKoreanText() {
        DecodedTextCandidate best = decoder.chooseBestDecodedText(List.of(
                new DecodedTextCandidate("broken", "?좎룞?쇿뜝", 0.75, 0.40, true),
                new DecodedTextCandidate("MS949", "삼성전자", 0.0, 1.0, true)
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
        EncodingProbeResult result = decoder.probe("?좎룞?쇿뜝?숈삕", null, "ks_c_5601-1987");

        assertEquals("UNRECOVERABLE", result.decodeStatus());
    }

    @Test
    void archiveCommandExposesEncodingProbe() {
        CommandLine commandLine = new CommandLine(new ArchiveCommand());

        assertTrue(commandLine.getSubcommands().containsKey("encoding-probe"));
        assertDoesNotThrow(() -> commandLine.parseArgs("encoding-probe", "sample.pst", "--limit", "10"));
    }
}
