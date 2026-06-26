package com.example.pstarchive.encoding;

import com.example.pstarchive.pst.SafePstFieldExtractor;
import com.example.pstarchive.util.TextPreviewer;
import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;
import com.pff.PSTObject;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Vector;

public class EncodingProbeService {
    private static final int PROP_SUBJECT = 0x0037;
    private static final int PROP_SENDER_NAME = 0x0c1a;
    private static final int PROP_DISPLAY_TO = 0x0e04;
    private static final int PROP_DISPLAY_CC = 0x0e03;
    private static final int PROP_BODY = 0x1000;
    private static final int PROP_HTML_BODY = 0x1013;
    private static final int PROP_FOLDER_NAME = 0x3001;
    private static final int PROP_MESSAGE_CODEPAGE = 0x3FFD;
    private static final int PROP_INTERNET_CODEPAGE = 0x3FDE;

    private final PstRawPropertyAccessor rawAccessor = new PstRawPropertyAccessor();
    private final BestEffortTextDecoder decoder = new BestEffortTextDecoder();

    public int probe(Path pstPath, int limit, PrintStream out) {
        out.println("ENCODING PROBE START");
        out.println("file: " + pstPath.toAbsolutePath().normalize());
        out.println("limit: " + limit);
        printRuntimeEncodingDiagnostics(out);
        out.println();

        ProbeState state = new ProbeState(limit);
        PSTFile pstFile = null;
        try {
            pstFile = new PSTFile(pstPath.toFile());
            scanFolder(pstFile.getRootFolder(), "/Root", state, out);
        } catch (Exception e) {
            state.fatalErrors++;
            out.println("[FATAL] " + e.getClass().getSimpleName() + ": " + safeMessage(e));
        } finally {
            if (pstFile != null) {
                try {
                    pstFile.close();
                } catch (IOException e) {
                    state.fatalErrors++;
                    out.println("[FATAL] close failed: " + e.getClass().getSimpleName() + ": " + safeMessage(e));
                }
            }
        }

        out.println("ENCODING PROBE SUMMARY");
        out.println("foldersVisited: " + state.foldersVisited);
        out.println("messagesProbed: " + state.messagesProbed);
        out.println("fatalErrors: " + state.fatalErrors);
        return state.fatalErrors == 0 ? 0 : 1;
    }

    private void printRuntimeEncodingDiagnostics(PrintStream out) {
        out.println("javaDefaultCharset: " + Charset.defaultCharset());
        out.println("native.encoding: " + property("native.encoding"));
        out.println("stdout.encoding: " + property("stdout.encoding"));
        out.println("sun.stdout.encoding: " + property("sun.stdout.encoding"));
        out.println("consoleCharset: " + consoleCharset());
    }

    private String property(String name) {
        return System.getProperty(name, "<none>");
    }

    private String consoleCharset() {
        if (System.console() == null) {
            return "<none>";
        }
        return System.console().charset().name();
    }

    private void scanFolder(PSTFolder folder, String folderPath, ProbeState state, PrintStream out) {
        if (folder == null || state.messagesProbed >= state.limit) {
            return;
        }
        state.foldersVisited++;
        String folderName = new SafePstFieldExtractor().stringValue(folder::getDisplayName);
        EncodingProbeResult folderProbe = probeText(folder, PROP_FOLDER_NAME, folderName, null);
        String decodedFolderPath = folderPath + " [" + folderProbe.decodedBestText() + "]";

        while (state.messagesProbed < state.limit) {
            PSTObject child;
            try {
                child = folder.getNextChild();
            } catch (Exception e) {
                out.println("[MESSAGE ERROR] " + folderPath + " getNextChild: " + e.getClass().getSimpleName() + ": " + safeMessage(e));
                break;
            }
            if (child == null) {
                break;
            }
            if (child instanceof PSTMessage message) {
                state.messagesProbed++;
                printMessageProbe(state.messagesProbed, decodedFolderPath, folderName, folderProbe, message, out);
            }
        }

        Vector<PSTFolder> children;
        try {
            children = folder.getSubFolders();
        } catch (Exception e) {
            out.println("[FOLDER ERROR] " + folderPath + " subfolders: " + e.getClass().getSimpleName() + ": " + safeMessage(e));
            return;
        }
        for (PSTFolder child : children) {
            if (state.messagesProbed >= state.limit) {
                return;
            }
            String childName = new SafePstFieldExtractor().stringValue(child::getDisplayName);
            scanFolder(child, folderPath + "/" + sanitizePathSegment(childName), state, out);
        }
    }

    private void printMessageProbe(long number, String folderPath, String folderRaw, EncodingProbeResult folderProbe,
                                   PSTMessage message, PrintStream out) {
        SafePstFieldExtractor fields = new SafePstFieldExtractor();
        String descriptor = fields.stringValue(message::getDescriptorNodeId);
        String subject = fields.stringValue(message::getSubject);
        String senderName = fields.stringValue(message::getSenderName);
        String to = fields.stringValue(message::getDisplayTo);
        String cc = fields.stringValue(message::getDisplayCC);
        String html = fields.stringValue(message::getBodyHTML);
        String body = fields.stringValue(message::getBody);

        String htmlMetaCharset = HtmlCharsetDetector.detect(html).orElse("<none>");
        Optional<String> messageCodepageHint = messageCodepageHint(message);
        Optional<String> internetCodepageHint = internetCodepageHint(message);
        String htmlCharsetHint = "<none>".equals(htmlMetaCharset) ? internetCodepageHint.orElse(null) : htmlMetaCharset;
        String bodyCharsetHint = messageCodepageHint.orElse(null);

        EncodingProbeResult subjectProbe = probeText(message, PROP_SUBJECT, subject, bodyCharsetHint);
        EncodingProbeResult senderProbe = probeText(message, PROP_SENDER_NAME, senderName, bodyCharsetHint);
        EncodingProbeResult toProbe = probeText(message, PROP_DISPLAY_TO, to, bodyCharsetHint);
        EncodingProbeResult ccProbe = probeText(message, PROP_DISPLAY_CC, cc, bodyCharsetHint);
        EncodingProbeResult bodyProbe = probeText(message, PROP_BODY, body, bodyCharsetHint);
        EncodingProbeResult htmlProbe = probeText(message, PROP_HTML_BODY, html, htmlCharsetHint);

        out.println("[MAIL " + number + " ENCODING PROBE]");
        out.println("descriptorNodeId: " + descriptor);
        out.println("folderRaw: " + TextPreviewer.preview(folderRaw, 160));
        out.println("folderDecoded: " + TextPreviewer.preview(folderProbe.decodedBestText(), 160));
        out.println("folderDecodeStatus: " + folderProbe.decodeStatus());
        printTextProbe("subject", subjectProbe, out);
        printTextProbe("senderName", senderProbe, out);
        printTextProbe("to", toProbe, out);
        printTextProbe("cc", ccProbe, out);
        out.println("htmlMetaCharset: " + htmlMetaCharset);
        out.println("messageCodepageHint: " + messageCodepageHint.orElse("<none>"));
        out.println("internetCodepageHint: " + internetCodepageHint.orElse("<none>"));
        printHtmlCandidates(htmlProbe, out);
        printTextProbe("body", bodyProbe, out);
        out.println();
    }

    private void printTextProbe(String label, EncodingProbeResult result, PrintStream out) {
        out.println(label + "RawGetter: " + TextPreviewer.preview(result.rawGetterText(), 160));
        out.println(label + "DecodedBest: " + TextPreviewer.preview(result.decodedBestText(), 160));
        out.println(label + "DecodeStatus: " + result.decodeStatus());
        out.println(label + "RawBytesAvailable: " + result.rawBytesAvailable());
        out.println(label + "BestLabel: " + result.bestLabel());
        out.println(label + "BrokenCharRatioBefore: " + format(result.brokenCharRatioBefore()));
        out.println(label + "BrokenCharRatioAfter: " + format(result.brokenCharRatioAfter()));
        out.println(label + "HangulRatioAfter: " + format(result.hangulRatioAfter()));
    }

    private void printHtmlCandidates(EncodingProbeResult result, PrintStream out) {
        out.println("htmlGetterPreview: " + TextPreviewer.preview(result.rawGetterText(), 160));
        out.println("htmlDecodedBestCharset: " + result.bestLabel());
        out.println("htmlDecodedBestPreview: " + TextPreviewer.preview(result.decodedBestText(), 500));
        for (DecodedTextCandidate candidate : result.candidates()) {
            String label = candidate.label().toLowerCase();
            if (label.contains("utf-8") || label.contains("ms949") || label.contains("euc-kr")) {
                out.println("htmlDecodedBy" + compactLabel(candidate.label()) + "Preview: "
                        + TextPreviewer.preview(candidate.text(), 160));
            }
        }
        out.println("brokenCharRatioBefore: " + format(result.brokenCharRatioBefore()));
        out.println("brokenCharRatioAfter: " + format(result.brokenCharRatioAfter()));
        out.println("hangulRatioAfter: " + format(result.hangulRatioAfter()));
    }

    private EncodingProbeResult probeText(PSTObject object, int propertyId, String getterText, String charsetHint) {
        Optional<byte[]> raw = rawAccessor.rawBytes(object, propertyId);
        return decoder.probe(getterText, raw.orElse(null), charsetHint);
    }

    private Optional<String> messageCodepageHint(PSTMessage message) {
        return rawAccessor.intValue(message, PROP_MESSAGE_CODEPAGE).map(this::codepageToCharsetName);
    }

    private Optional<String> internetCodepageHint(PSTMessage message) {
        return rawAccessor.intValue(message, PROP_INTERNET_CODEPAGE).map(this::codepageToCharsetName);
    }

    private String codepageToCharsetName(int codepage) {
        return switch (codepage) {
            case 65001 -> "UTF-8";
            case 949, 51949 -> "MS949";
            case 5601 -> "EUC-KR";
            default -> "cp" + codepage;
        };
    }

    private String compactLabel(String label) {
        String normalized = label.replace("getter-as-", "").replace("to-", "");
        if (normalized.equalsIgnoreCase("UTF-8")) {
            return "Utf8";
        }
        if (normalized.equalsIgnoreCase("MS949") || normalized.contains("MS949")) {
            return "Ms949";
        }
        if (normalized.equalsIgnoreCase("EUC-KR") || normalized.contains("EUC-KR")) {
            return "EucKr";
        }
        return normalized.replaceAll("[^A-Za-z0-9]", "");
    }

    private String sanitizePathSegment(String segment) {
        if (segment == null || segment.isBlank() || "<null>".equals(segment)) {
            return "<unnamed>";
        }
        return segment.replace("\\", "_").replace("/", "_");
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "no message";
        }
        return message.replace("\r", " ").replace("\n", " ");
    }

    private static class ProbeState {
        private final int limit;
        private long foldersVisited;
        private long messagesProbed;
        private long fatalErrors;

        private ProbeState(int limit) {
            this.limit = limit;
        }
    }
}
