package com.example.pstarchive.inspect;

import com.example.pstarchive.util.FileSizeUtils;
import com.example.pstarchive.util.TextPreviewer;

import java.io.PrintStream;
import java.util.List;
import java.util.Locale;

public class InspectionFormatter {
    private static final int SAMPLE_PREVIEW_LIMIT = 300;
    private static final int DETAIL_PREVIEW_LIMIT = 1000;

    private final PrintStream out;

    public InspectionFormatter(PrintStream out) {
        this.out = out;
    }

    public void printInspection(StoreInspectionResult result) {
        out.println("STORE INSPECTION");
        out.println("file: " + result.storePath());
        out.println("sizeBytes: " + result.sizeBytes());
        out.println("size: " + FileSizeUtils.humanReadable(result.sizeBytes()));
        out.println();

        out.println("TABLE COUNTS");
        out.println("folders: " + result.foldersCount());
        out.println("messages: " + result.messagesCount());
        out.println("index_runs: " + result.indexRunsCount());
        out.println("index_errors: " + result.indexErrorsCount());
        out.println();

        out.println("LATEST INDEX RUN");
        if (result.latestRun().isPresent()) {
            printLatestRun(result.latestRun().get());
        } else {
            out.println("<none>");
        }
        out.println();

        out.println("QUALITY SUMMARY");
        printStatusCounts("parse_status", result.parseStatusCounts());
        printStatusCounts("subject_status", result.subjectStatusCounts());
        printStatusCounts("body_text_status", result.bodyTextStatusCounts());
        printStatusCounts("body_html_status", result.bodyHtmlStatusCounts());
        printStatusCounts("body_html_text_status", result.bodyHtmlTextStatusCounts());
        out.println("subjectNullCount: " + result.subjectNullCount());
        out.println("bodyTextNullCount: " + result.bodyTextNullCount());
        out.println("bodyHtmlNullCount: " + result.bodyHtmlNullCount());
        out.println("bodyHtmlTextNullCount: " + result.bodyHtmlTextNullCount());
        out.println("averageBodyTextLength: " + format(result.averageBodyTextLength()));
        out.println("averageBodyHtmlTextLength: " + format(result.averageBodyHtmlTextLength()));
        out.println("sentAtMin: " + result.minSentAt());
        out.println("sentAtMax: " + result.maxSentAt());
        out.println("receivedAtMin: " + result.minReceivedAt());
        out.println("receivedAtMax: " + result.maxReceivedAt());
    }

    public void printSamples(List<MessageSample> samples) {
        out.println("MESSAGE SAMPLES");
        out.println("count: " + samples.size());
        out.println();
        for (MessageSample sample : samples) {
            out.println("[MESSAGE " + sample.id() + "]");
            out.println("folder_path: " + sample.folderPath());
            out.println("descriptor_node_id: " + value(sample.descriptorNodeId()));
            out.println("subject: " + value(sample.subject()));
            out.println("subject_status: " + value(sample.subjectStatus()));
            out.println("sender_name: " + value(sample.senderName()));
            out.println("sender_email: " + value(sample.senderEmail()));
            out.println("recipients: " + value(sample.recipients()));
            out.println("cc: " + value(sample.cc()));
            out.println("sent_at: " + value(sample.sentAt()));
            out.println("received_at: " + value(sample.receivedAt()));
            out.println("body_text_length: " + value(sample.bodyTextLength()));
            out.println("body_html_length: " + value(sample.bodyHtmlLength()));
            out.println("body_html_text_length: " + value(sample.bodyHtmlTextLength()));
            out.println("body_text_status: " + value(sample.bodyTextStatus()));
            out.println("body_html_status: " + value(sample.bodyHtmlStatus()));
            out.println("body_html_text_status: " + value(sample.bodyHtmlTextStatus()));
            out.println("body_text_preview:");
            out.println(TextPreviewer.preview(sample.bodyText(), SAMPLE_PREVIEW_LIMIT));
            out.println("body_html_text_preview:");
            out.println(TextPreviewer.preview(sample.bodyHtmlText(), SAMPLE_PREVIEW_LIMIT));
            out.println();
        }
    }

    public void printMessage(MessageDetail detail) {
        out.println("MESSAGE DETAIL");
        out.println("id: " + detail.id());
        out.println("folder_id: " + value(detail.folderId()));
        out.println("folder_path: " + value(detail.folderPath()));
        out.println("descriptor_node_id: " + value(detail.descriptorNodeId()));
        out.println("internet_message_id: " + value(detail.internetMessageId()));
        out.println("parse_status: " + value(detail.parseStatus()));
        out.println("indexed_at: " + value(detail.indexedAt()));
        out.println();
        out.println("subject: " + value(detail.subject()));
        out.println("subject_status: " + value(detail.subjectStatus()));
        out.println("subject_source: " + value(detail.subjectSource()));
        out.println("sender_name: " + value(detail.senderName()));
        out.println("sender_email: " + value(detail.senderEmail()));
        out.println("recipients: " + value(detail.recipients()));
        out.println("cc: " + value(detail.cc()));
        out.println("sent_at: " + value(detail.sentAt()));
        out.println("received_at: " + value(detail.receivedAt()));
        out.println();
        out.println("body_text_length: " + value(detail.bodyTextLength()));
        out.println("body_text_status: " + value(detail.bodyTextStatus()));
        out.println("body_text_source: " + value(detail.bodyTextSource()));
        out.println("body_html_length: " + value(detail.bodyHtmlLength()));
        out.println("body_html_status: " + value(detail.bodyHtmlStatus()));
        out.println("body_html_source: " + value(detail.bodyHtmlSource()));
        out.println("body_html_text_length: " + value(detail.bodyHtmlTextLength()));
        out.println("body_html_text_status: " + value(detail.bodyHtmlTextStatus()));
        out.println();
        out.println("body_text_preview:");
        out.println(TextPreviewer.preview(detail.bodyText(), DETAIL_PREVIEW_LIMIT));
        out.println();
        out.println("body_html_text_preview:");
        out.println(TextPreviewer.preview(detail.bodyHtmlText(), DETAIL_PREVIEW_LIMIT));
    }

    public void printQualityReport(StoreQualityReport report) {
        out.println("QUALITY REPORT");
        out.println("status: " + report.decision());
        out.println();
        out.println("Reasons:");
        report.reasons().forEach(reason -> out.println("- " + reason));
        out.println();
        if (!report.blockingIssues().isEmpty()) {
            out.println("Blocking issues:");
            report.blockingIssues().forEach(issue -> out.println("- " + issue));
            out.println();
        }
        if (!report.warnings().isEmpty()) {
            out.println("Warnings:");
            report.warnings().forEach(warning -> out.println("- " + warning));
            out.println();
        }
        if (!report.topErrors().isEmpty()) {
            out.println("Top index_errors:");
            report.topErrors().forEach(error -> out.println("- " + error));
            out.println();
        }
        out.println("Decision:");
        if (report.decision() == StoreQualityDecision.NOT_READY) {
            out.println("Phase 3B should not proceed yet.");
        } else if (report.decision() == StoreQualityDecision.READY_WITH_WARNINGS) {
            out.println("Phase 3B can proceed, but inspect warnings first.");
        } else {
            out.println("Phase 3B can proceed.");
        }
    }

    private void printLatestRun(LatestIndexRun run) {
        out.println("id: " + run.id());
        out.println("pstPath: " + value(run.pstPath()));
        out.println("startedAt: " + value(run.startedAt()));
        out.println("finishedAt: " + value(run.finishedAt()));
        out.println("limitCount: " + value(run.limitCount()));
        out.println("replaceMode: " + run.replaceMode());
        out.println("status: " + value(run.status()));
        out.println("foldersVisited: " + run.foldersVisited());
        out.println("messagesSeen: " + run.messagesSeen());
        out.println("messagesSaved: " + run.messagesSaved());
        out.println("messageErrors: " + run.messageErrors());
        out.println("fieldErrors: " + run.fieldErrors());
        out.println("fatalErrors: " + run.fatalErrors());
        out.println("okFields: " + run.okFields());
        out.println("degradedFields: " + run.degradedFields());
        out.println("unrecoverableFields: " + run.unrecoverableFields());
        out.println("nullFields: " + run.nullFields());
    }

    private void printStatusCounts(String title, List<StatusCount> counts) {
        out.println(title + ":");
        if (counts.isEmpty()) {
            out.println("  <none>: 0");
            return;
        }
        for (StatusCount count : counts) {
            out.println("  " + count.status() + ": " + count.count());
        }
    }

    private String value(Object value) {
        return value == null ? "<null>" : String.valueOf(value);
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}

