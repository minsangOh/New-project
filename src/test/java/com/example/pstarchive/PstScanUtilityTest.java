package com.example.pstarchive;

import com.example.pstarchive.cli.ArchiveCommand;
import com.example.pstarchive.pst.SafePstFieldExtractor;
import com.example.pstarchive.util.HtmlTextExtractor;
import com.example.pstarchive.util.TextPreviewer;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class PstScanUtilityTest {
    @Test
    void textPreviewHandlesNullEmptyAndLongText() {
        assertEquals("<null>", TextPreviewer.preview(null));
        assertEquals("<empty>", TextPreviewer.preview(""));
        assertEquals("abc", TextPreviewer.preview("abc", 10));
        assertEquals("abcde...", TextPreviewer.preview("abcdef", 5));
    }

    @Test
    void htmlTextExtractorConvertsHtmlToText() {
        assertEquals("Hello Samsung", HtmlTextExtractor.toText("<html><body><b>Hello</b> Samsung</body></html>"));
        assertNull(HtmlTextExtractor.toText(null));
        assertEquals("", HtmlTextExtractor.toText(""));
    }

    @Test
    void safeFieldExtractorFormatsNullEmptyAndErrors() {
        SafePstFieldExtractor extractor = new SafePstFieldExtractor();

        assertEquals("<null>", extractor.stringValue(() -> null));
        assertEquals("<empty>", extractor.stringValue(() -> ""));
        assertEquals("value", extractor.stringValue(() -> "value"));
        assertTrue(extractor.stringValue(() -> {
            throw new IllegalStateException("broken field");
        }).startsWith("<error: IllegalStateException: broken field>"));
        assertEquals(1, extractor.fieldErrors());
    }

    @Test
    void archiveCommandExposesScanCommands() {
        CommandLine commandLine = new CommandLine(new ArchiveCommand());

        assertTrue(commandLine.getSubcommands().containsKey("scan-file"));
        assertTrue(commandLine.getSubcommands().containsKey("scan-pst"));
        assertDoesNotThrow(() -> commandLine.parseArgs("scan-file", "sample.pst", "--limit", "10"));
        assertDoesNotThrow(() -> commandLine.parseArgs("scan-pst", "sample-id", "--limit", "10"));
    }
}
