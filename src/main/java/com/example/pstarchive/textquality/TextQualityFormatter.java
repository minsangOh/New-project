package com.example.pstarchive.textquality;

import com.example.pstarchive.inspect.MessageDetail;
import com.example.pstarchive.util.TextPreviewer;

import java.io.PrintStream;
import java.util.List;
import java.util.Locale;

public class TextQualityFormatter {
    private static final int RAW_PREVIEW_LIMIT = 500;
    private static final int CODE_POINT_LIMIT = 80;

    private final PrintStream out;
    private final TextQualityAnalyzer analyzer = new TextQualityAnalyzer();

    public TextQualityFormatter(PrintStream out) {
        this.out = out;
    }

    public void printReport(TextQualityReport report) {
        out.println("TEXT QUALITY REPORT");
        out.println("messagesChecked: " + report.messagesChecked());
        out.println("fieldsChecked: " + report.fieldsChecked());
        out.println("okFields: " + report.okFields());
        out.println("suspectFields: " + report.suspectFields());
        out.println("degradedFields: " + report.degradedFields());
        out.println("brokenFields: " + report.brokenFields());
        out.println("nullFields: " + report.nullFields());
        out.println("nulCharFields: " + report.nulCharFields());
        out.println("questionHeavyFields: " + report.questionHeavyFields());
        out.println("mojibakeFields: " + report.mojibakeFields());
        out.println("statusMismatchCount: " + report.statusMismatchCount());
        out.println();
        out.println("STATUS MISMATCH EXAMPLES");
        if (report.mismatchExamples().isEmpty()) {
            out.println("<none>");
            return;
        }
        for (TextFieldDiagnostic example : report.mismatchExamples()) {
            printDiagnostic(example);
            out.println();
        }
    }

    public void printRawDump(MessageDetail detail) {
        out.println("MESSAGE RAW DUMP");
        out.println("messageId: " + detail.id());
        out.println("descriptorNodeId: " + value(detail.descriptorNodeId()));
        out.println("internetMessageId: " + value(detail.internetMessageId()));
        out.println("parseStatus: " + value(detail.parseStatus()));
        out.println();
        printField("folder_path", null, detail.folderPath());
        printField("subject", detail.subjectStatus(), detail.subject());
        printField("sender_name", null, detail.senderName());
        printField("sender_email", null, detail.senderEmail());
        printField("recipients", null, detail.recipients());
        printField("cc", null, detail.cc());
        printField("body_text", detail.bodyTextStatus(), detail.bodyText());
        printField("body_html_text", detail.bodyHtmlTextStatus(), detail.bodyHtmlText());
    }

    private void printDiagnostic(TextFieldDiagnostic diagnostic) {
        out.println("messageId: " + diagnostic.messageId());
        out.println("field: " + diagnostic.field());
        out.println("storedStatus: " + value(diagnostic.storedStatus()));
        out.println("diagnosedStatus: " + diagnostic.quality().level());
        out.println("reason: " + diagnostic.quality().reason());
        out.println("warnings: " + warnings(diagnostic.quality().warnings()));
        out.println("preview:");
        out.println(diagnostic.quality().sanitizedPreview());
    }

    private void printField(String field, String storedStatus, String value) {
        TextQualityResult quality = analyzer.diagnose(value);
        out.println("[FIELD " + field + "]");
        out.println("storedStatus: " + value(storedStatus));
        out.println("diagnosedStatus: " + quality.level());
        out.println("reason: " + quality.reason());
        out.println("length: " + (value == null ? 0 : value.length()));
        out.println("nulCharCount: " + quality.nulCharCount());
        out.println("questionMarkCount: " + quality.questionMarkCount());
        out.println("questionMarkRatio: " + String.format(Locale.ROOT, "%.4f", quality.questionMarkRatio()));
        out.println("repeatedQuestionRuns: " + quality.repeatedQuestionRuns());
        out.println("mojibakePatternCount: " + quality.mojibakePatternCount());
        out.println("warnings: " + warnings(quality.warnings()));
        out.println("preview:");
        out.println(TextPreviewer.preview(StoredTextSanitizer.sanitize(value), RAW_PREVIEW_LIMIT));
        out.println("codePoints:");
        out.println(codePoints(value));
        out.println();
    }

    private String codePoints(String value) {
        if (value == null) {
            return "<null>";
        }
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (int i = 0; i < value.length() && count < CODE_POINT_LIMIT; ) {
            int codePoint = value.codePointAt(i);
            if (count > 0) {
                builder.append(' ');
            }
            builder.append(String.format(Locale.ROOT, "U+%04X", codePoint));
            i += Character.charCount(codePoint);
            count++;
        }
        if (value.codePointCount(0, value.length()) > CODE_POINT_LIMIT) {
            builder.append(" ...");
        }
        return builder.length() == 0 ? "<empty>" : builder.toString();
    }

    private String warnings(List<String> warnings) {
        return warnings.isEmpty() ? "<none>" : String.join(",", warnings);
    }

    private String value(Object value) {
        return value == null ? "<null>" : String.valueOf(value);
    }
}
