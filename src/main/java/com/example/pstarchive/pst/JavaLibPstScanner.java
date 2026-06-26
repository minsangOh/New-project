package com.example.pstarchive.pst;

import com.example.pstarchive.util.HtmlTextExtractor;
import com.example.pstarchive.util.TextPreviewer;
import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;
import com.pff.PSTObject;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Vector;

public class JavaLibPstScanner implements PstScanner {
    private static final String PARSER_VERSION = "java-libpst-0.9.3";

    @Override
    public PstScanSummary scan(Path pstPath, PstScanOptions options) {
        ScanState state = new ScanState(options.limit());
        PrintStream out = options.output();

        out.println("PST SCAN START");
        out.println("file: " + pstPath.toAbsolutePath().normalize());
        out.println("limit: " + options.limit());
        out.println("mode: " + options.mode());
        out.println();

        PSTFile pstFile = null;
        try {
            pstFile = new PSTFile(pstPath.toFile());
            PSTFolder root = pstFile.getRootFolder();
            scanFolder(root, "/Root", state, out);
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

        PstScanSummary summary = new PstScanSummary(
                state.foldersVisited,
                state.messagesScanned,
                state.fieldErrors,
                state.messageErrors,
                state.fatalErrors,
                PARSER_VERSION,
                "scan complete"
        );
        printSummary(summary, out);
        return summary;
    }

    private void scanFolder(PSTFolder folder, String folderPath, ScanState state, PrintStream out) {
        if (folder == null) {
            return;
        }
        state.foldersVisited++;

        SafePstFieldExtractor folderFields = new SafePstFieldExtractor();
        String name = folderFields.stringValue(folder::getDisplayName);
        String itemCount = folderFields.stringValue(folder::getContentCount);
        Vector<PSTFolder> subFolders = safeSubFolders(folder, state, out, folderPath);
        state.fieldErrors += folderFields.fieldErrors();

        out.println("[FOLDER] " + folderPath);
        out.println("name: " + name);
        out.println("itemCount: " + itemCount);
        out.println("subFolderCount: " + subFolders.size());
        out.println();

        scanMessagesInFolder(folder, folderPath, state, out);

        for (PSTFolder child : subFolders) {
            String childName = new SafePstFieldExtractor().stringValue(child::getDisplayName);
            scanFolder(child, folderPath + "/" + sanitizePathSegment(childName), state, out);
        }
    }

    private Vector<PSTFolder> safeSubFolders(PSTFolder folder, ScanState state, PrintStream out, String folderPath) {
        try {
            return folder.getSubFolders();
        } catch (Exception e) {
            state.messageErrors++;
            out.println("[FOLDER ERROR] " + folderPath + " subfolders: " + e.getClass().getSimpleName() + ": " + safeMessage(e));
            return new Vector<>();
        }
    }

    private void scanMessagesInFolder(PSTFolder folder, String folderPath, ScanState state, PrintStream out) {
        while (state.messagesScanned < state.limit) {
            PSTObject child;
            try {
                child = folder.getNextChild();
            } catch (Exception e) {
                state.messageErrors++;
                out.println("[MESSAGE ERROR] " + folderPath + " getNextChild: " + e.getClass().getSimpleName() + ": " + safeMessage(e));
                return;
            }
            if (child == null) {
                return;
            }
            if (child instanceof PSTMessage message) {
                state.messagesScanned++;
                printMessage(state.messagesScanned, folderPath, message, state, out);
            }
        }
    }

    private void printMessage(long number, String folderPath, PSTMessage message, ScanState state, PrintStream out) {
        try {
            SafePstFieldExtractor fields = new SafePstFieldExtractor();
            String plainBody = fields.stringValue(message::getBody);
            String htmlBody = fields.stringValue(message::getBodyHTML);
            String htmlText = htmlBody.startsWith("<error:") || "<null>".equals(htmlBody)
                    ? htmlBody
                    : fields.stringValue(() -> HtmlTextExtractor.toText("<empty>".equals(htmlBody) ? "" : htmlBody));

            PstMailPreview preview = new PstMailPreview(
                    folderPath,
                    fields.stringValue(message::getDescriptorNodeId),
                    fields.stringValue(message::getInternetMessageId),
                    fields.stringValue(message::getSubject),
                    fields.stringValue(message::getSenderName),
                    fields.stringValue(message::getSenderEmailAddress),
                    fields.stringValue(message::getDisplayTo),
                    fields.stringValue(message::getDisplayCC),
                    fields.dateValue(message::getClientSubmitTime),
                    fields.dateValue(message::getMessageDeliveryTime),
                    TextPreviewer.preview(toPreviewSource(plainBody)),
                    TextPreviewer.preview(toPreviewSource(htmlBody)),
                    TextPreviewer.preview(toPreviewSource(htmlText))
            );
            state.fieldErrors += fields.fieldErrors();
            printMailPreview(number, preview, out);
        } catch (Exception e) {
            state.messageErrors++;
            out.println("[MESSAGE ERROR] " + folderPath + " message preview: " + e.getClass().getSimpleName() + ": " + safeMessage(e));
        }
    }

    private String toPreviewSource(String value) {
        if ("<null>".equals(value)) {
            return null;
        }
        if ("<empty>".equals(value)) {
            return "";
        }
        return value;
    }

    private void printMailPreview(long number, PstMailPreview preview, PrintStream out) {
        out.println("[MAIL " + number + "]");
        out.println("folder: " + preview.folderPath());
        out.println("descriptorNodeId: " + preview.descriptorNodeId());
        out.println("internetMessageId: " + preview.internetMessageId());
        out.println("subject: " + preview.subject());
        out.println("senderName: " + preview.senderName());
        out.println("senderEmail: " + preview.senderEmail());
        out.println("to: " + preview.to());
        out.println("cc: " + preview.cc());
        out.println("sentAt: " + preview.sentAt());
        out.println("receivedAt: " + preview.receivedAt());
        out.println();
        out.println("plainBodyPreview:");
        out.println(preview.plainBodyPreview());
        out.println();
        out.println("htmlBodyPreview:");
        out.println(preview.htmlBodyPreview());
        out.println();
        out.println("htmlTextPreview:");
        out.println(preview.htmlTextPreview());
        out.println();
    }

    private void printSummary(PstScanSummary summary, PrintStream out) {
        out.println("SCAN SUMMARY");
        out.println("foldersVisited: " + summary.foldersVisited());
        out.println("messagesScanned: " + summary.messagesScanned());
        out.println("fieldErrors: " + summary.fieldErrors());
        out.println("messageErrors: " + summary.messageErrors());
        out.println("fatalErrors: " + summary.fatalErrors());
    }

    private String sanitizePathSegment(String segment) {
        if (segment == null || segment.isBlank() || "<null>".equals(segment)) {
            return "<unnamed>";
        }
        return segment.replace("\\", "_").replace("/", "_");
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "no message";
        }
        return message.replace("\r", " ").replace("\n", " ");
    }

    private static class ScanState {
        private final int limit;
        private long foldersVisited;
        private long messagesScanned;
        private long fieldErrors;
        private long messageErrors;
        private long fatalErrors;

        private ScanState(int limit) {
            this.limit = limit;
        }
    }
}
