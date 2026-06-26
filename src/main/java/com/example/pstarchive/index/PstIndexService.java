package com.example.pstarchive.index;

import com.example.pstarchive.encoding.ExtractedText;
import com.example.pstarchive.encoding.TextRecoveryStatus;
import com.example.pstarchive.pst.ExtractedFolder;
import com.example.pstarchive.pst.ExtractedMail;
import com.example.pstarchive.pst.PstMailExtractor;
import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;
import com.pff.PSTObject;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Vector;

public class PstIndexService {
    private static final int COMMIT_BATCH_SIZE = 100;

    private final PstMailExtractor extractor;

    public PstIndexService() {
        this(new PstMailExtractor());
    }

    public PstIndexService(PstMailExtractor extractor) {
        this.extractor = extractor;
    }

    public PstIndexSummary index(PstIndexOptions options) {
        long started = System.currentTimeMillis();
        PrintStream out = options.output();
        State state = new State(options.limit());

        out.println("INDEX START");
        out.println("file: " + options.pstPath().toAbsolutePath().normalize());
        out.println("out: " + options.storePath().toAbsolutePath().normalize());
        out.println("limit: " + options.limit());
        out.println("replace: " + options.replace());
        out.println();

        try {
            prepareStorePath(options.storePath());
        } catch (IOException e) {
            state.fatalErrors++;
            out.println("[FATAL] prepare store: " + e.getClass().getSimpleName() + ": " + safeMessage(e));
            return finishSummary(state, started, out);
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + options.storePath().toAbsolutePath())) {
            ShardStoreSchema.migrate(connection);
            connection.setAutoCommit(false);
            if (options.replace()) {
                ShardStoreSchema.replaceData(connection);
            }
            try (MessageStoreWriter writer = new MessageStoreWriter(connection)) {
                long runId = writer.startRun(options.pstPath().toAbsolutePath().normalize().toString(), options.limit(), options.replace());
                PSTFile pstFile = null;
                try {
                    pstFile = new PSTFile(options.pstPath().toFile());
                    scanFolder(pstFile.getRootFolder(), "/Root", null, state, writer, out);
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
                PstIndexSummary summary = buildSummary(state, started);
                writer.finishRun(runId, summary);
                connection.commit();
                printSummary(summary, out);
                return summary;
            } catch (Exception e) {
                connection.rollback();
                state.fatalErrors++;
                out.println("[FATAL] sqlite write: " + e.getClass().getSimpleName() + ": " + safeMessage(e));
            }
        } catch (Exception e) {
            state.fatalErrors++;
            out.println("[FATAL] sqlite open: " + e.getClass().getSimpleName() + ": " + safeMessage(e));
        }

        return finishSummary(state, started, out);
    }

    private void scanFolder(PSTFolder folder, String folderPath, Long parentId, State state,
                            MessageStoreWriter writer, PrintStream out) throws Exception {
        if (folder == null || state.reachedLimit()) {
            return;
        }
        Vector<PSTFolder> subFolders = safeSubFolders(folder, folderPath, state, writer);
        ExtractedFolder extractedFolder = extractor.extractFolder(folder, parentId, folderPath, subFolders);
        long folderId = writer.writeFolder(extractedFolder);
        state.foldersVisited++;
        state.pendingWrites++;
        commitIfNeeded(writer, state);

        scanMessages(folder, folderPath, folderId, state, writer, out);

        for (PSTFolder child : subFolders) {
            if (state.reachedLimit()) {
                return;
            }
            String childName = childName(child);
            scanFolder(child, folderPath + "/" + sanitizePathSegment(childName), folderId, state, writer, out);
        }
    }

    private void scanMessages(PSTFolder folder, String folderPath, long folderId, State state,
                              MessageStoreWriter writer, PrintStream out) throws Exception {
        while (!state.reachedLimit()) {
            PSTObject child;
            try {
                child = folder.getNextChild();
            } catch (Exception e) {
                state.messageErrors++;
                writer.writeError(new IndexFieldError(folderPath, null, "message", "getNextChild",
                        e.getClass().getSimpleName(), safeMessage(e)));
                return;
            }
            if (child == null) {
                return;
            }
            if (child instanceof PSTMessage message) {
                state.messagesSeen++;
                try {
                    ExtractedMail mail = extractor.extractMail(message, folderId, folderPath);
                    countStatuses(state, mail);
                    for (IndexFieldError error : mail.errors()) {
                        state.fieldErrors++;
                        writer.writeError(error);
                    }
                    writer.writeMessage(mail);
                    state.messagesSaved++;
                    state.pendingWrites++;
                    if (state.messagesSaved % COMMIT_BATCH_SIZE == 0) {
                        printProgress(state, out);
                    }
                    commitIfNeeded(writer, state);
                } catch (Exception e) {
                    state.messageErrors++;
                    writer.writeError(new IndexFieldError(folderPath, null, "message", "extract",
                            e.getClass().getSimpleName(), safeMessage(e)));
                }
            }
        }
    }

    private Vector<PSTFolder> safeSubFolders(PSTFolder folder, String folderPath, State state, MessageStoreWriter writer) throws Exception {
        try {
            return folder.getSubFolders();
        } catch (Exception e) {
            state.messageErrors++;
            writer.writeError(new IndexFieldError(folderPath, null, "folder", "subfolders",
                    e.getClass().getSimpleName(), safeMessage(e)));
            return new Vector<>();
        }
    }

    private void commitIfNeeded(MessageStoreWriter writer, State state) throws Exception {
        if (state.pendingWrites >= COMMIT_BATCH_SIZE) {
            writer.commit();
            state.pendingWrites = 0;
        }
    }

    private void countStatuses(State state, ExtractedMail mail) {
        countStatus(state, mail.subject());
        countStatus(state, mail.senderName());
        countStatus(state, mail.recipients());
        countStatus(state, mail.cc());
        countStatus(state, mail.bodyText());
        countStatus(state, mail.bodyHtml());
        countStatus(state, mail.bodyHtmlText());
    }

    private void countStatus(State state, ExtractedText text) {
        TextRecoveryStatus status = text == null ? TextRecoveryStatus.NULL : text.status();
        switch (status) {
            case OK -> state.okFields++;
            case DEGRADED -> state.degradedFields++;
            case UNRECOVERABLE -> state.unrecoverableFields++;
            case NULL -> state.nullFields++;
            case ERROR -> state.errorFields++;
        }
    }

    private void printProgress(State state, PrintStream out) {
        out.println("[PROGRESS]");
        out.println("foldersVisited: " + state.foldersVisited);
        out.println("messagesSeen: " + state.messagesSeen);
        out.println("messagesSaved: " + state.messagesSaved);
        out.println("fieldErrors: " + state.fieldErrors);
        out.println("degradedFields: " + state.degradedFields);
        out.println("unrecoverableFields: " + state.unrecoverableFields);
        out.println();
    }

    private PstIndexSummary finishSummary(State state, long started, PrintStream out) {
        PstIndexSummary summary = buildSummary(state, started);
        printSummary(summary, out);
        return summary;
    }

    private PstIndexSummary buildSummary(State state, long started) {
        long elapsed = System.currentTimeMillis() - started;
        String status;
        if (state.fatalErrors > 0) {
            status = state.messagesSaved > 0 ? "PARTIAL_SUCCESS" : "FAILED";
        } else if (state.messageErrors > 0 || state.fieldErrors > 0) {
            status = "PARTIAL_SUCCESS";
        } else {
            status = "SUCCESS";
        }
        return new PstIndexSummary(
                state.foldersVisited,
                state.messagesSeen,
                state.messagesSaved,
                state.messageErrors,
                state.fieldErrors,
                state.fatalErrors,
                state.okFields,
                state.degradedFields,
                state.unrecoverableFields,
                state.nullFields,
                state.errorFields,
                elapsed,
                status
        );
    }

    private void printSummary(PstIndexSummary summary, PrintStream out) {
        out.println("INDEX SUMMARY");
        out.println("foldersVisited: " + summary.foldersVisited());
        out.println("messagesSeen: " + summary.messagesSeen());
        out.println("messagesSaved: " + summary.messagesSaved());
        out.println("messageErrors: " + summary.messageErrors());
        out.println("fieldErrors: " + summary.fieldErrors());
        out.println("fatalErrors: " + summary.fatalErrors());
        out.println("okFields: " + summary.okFields());
        out.println("degradedFields: " + summary.degradedFields());
        out.println("unrecoverableFields: " + summary.unrecoverableFields());
        out.println("nullFields: " + summary.nullFields());
        out.println("elapsedMs: " + summary.elapsedMs());
        out.println("status: " + summary.status());
    }

    private void prepareStorePath(Path storePath) throws IOException {
        Path parent = storePath.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private String childName(PSTFolder folder) {
        try {
            String name = folder.getDisplayName();
            if (name == null || name.isBlank()) {
                return "<unnamed>";
            }
            return name;
        } catch (Exception e) {
            return "<unnamed>";
        }
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

    private static class State {
        private final int limit;
        private long foldersVisited;
        private long messagesSeen;
        private long messagesSaved;
        private long messageErrors;
        private long fieldErrors;
        private long fatalErrors;
        private long okFields;
        private long degradedFields;
        private long unrecoverableFields;
        private long nullFields;
        private long errorFields;
        private long pendingWrites;

        private State(int limit) {
            this.limit = limit < 0 ? Integer.MAX_VALUE : limit;
        }

        private boolean reachedLimit() {
            return messagesSeen >= limit;
        }
    }
}
