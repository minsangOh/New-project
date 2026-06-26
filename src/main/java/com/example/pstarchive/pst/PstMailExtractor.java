package com.example.pstarchive.pst;

import com.example.pstarchive.encoding.EncodingAwareTextExtractor;
import com.example.pstarchive.encoding.ExtractedText;
import com.example.pstarchive.encoding.HtmlCharsetDetector;
import com.example.pstarchive.encoding.TextRecoveryStatus;
import com.example.pstarchive.index.IndexFieldError;
import com.example.pstarchive.util.HtmlTextExtractor;
import com.pff.PSTFolder;
import com.pff.PSTMessage;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Vector;

public class PstMailExtractor {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final EncodingAwareTextExtractor textExtractor;

    public PstMailExtractor() {
        this(new EncodingAwareTextExtractor());
    }

    public PstMailExtractor(EncodingAwareTextExtractor textExtractor) {
        this.textExtractor = textExtractor;
    }

    public ExtractedFolder extractFolder(PSTFolder folder, Long parentId, String folderPath, Vector<PSTFolder> subFolders) {
        ExtractedText displayName = textExtractor.extract(
                folder,
                EncodingAwareTextExtractor.PROP_FOLDER_NAME,
                folder::getDisplayName,
                null
        );
        return new ExtractedFolder(
                null,
                parentId,
                folderPath,
                displayName.text(),
                safeLong(folder::getDescriptorNodeId),
                safeInteger(folder::getContentCount),
                subFolders == null ? null : subFolders.size()
        );
    }

    public ExtractedMail extractMail(PSTMessage message, Long folderId, String folderPath) {
        List<IndexFieldError> errors = new ArrayList<>();
        Long descriptorNodeId = safeLong(message::getDescriptorNodeId);
        String internetMessageId = safeString("internet_message_id", message::getInternetMessageId, folderPath, descriptorNodeId, errors);
        String senderEmail = safeString("sender_email", message::getSenderEmailAddress, folderPath, descriptorNodeId, errors);
        String sentAt = safeDate("sent_at", message::getClientSubmitTime, folderPath, descriptorNodeId, errors);
        String receivedAt = safeDate("received_at", message::getMessageDeliveryTime, folderPath, descriptorNodeId, errors);

        Optional<String> messageCodepageHint = textExtractor.messageCodepageHint(message);

        ExtractedText subject = extractText("subject", message, EncodingAwareTextExtractor.PROP_SUBJECT,
                message::getSubject, messageCodepageHint.orElse(null), folderPath, descriptorNodeId, errors);
        ExtractedText senderName = extractText("sender_name", message, EncodingAwareTextExtractor.PROP_SENDER_NAME,
                message::getSenderName, messageCodepageHint.orElse(null), folderPath, descriptorNodeId, errors);
        ExtractedText recipients = extractText("recipients", message, EncodingAwareTextExtractor.PROP_DISPLAY_TO,
                message::getDisplayTo, messageCodepageHint.orElse(null), folderPath, descriptorNodeId, errors);
        ExtractedText cc = extractText("cc", message, EncodingAwareTextExtractor.PROP_DISPLAY_CC,
                message::getDisplayCC, messageCodepageHint.orElse(null), folderPath, descriptorNodeId, errors);
        ExtractedText bodyText = extractText("body_text", message, EncodingAwareTextExtractor.PROP_BODY,
                message::getBody, messageCodepageHint.orElse(null), folderPath, descriptorNodeId, errors);

        String htmlGetter = safeString("body_html_getter_for_charset", message::getBodyHTML, folderPath, descriptorNodeId, errors);
        String htmlMetaCharset = HtmlCharsetDetector.detect(htmlGetter).orElse(null);
        String htmlCharsetHint = htmlMetaCharset == null
                ? textExtractor.internetCodepageHint(message).orElse(messageCodepageHint.orElse(null))
                : htmlMetaCharset;
        ExtractedText bodyHtml = extractText("body_html", message, EncodingAwareTextExtractor.PROP_HTML_BODY,
                message::getBodyHTML, htmlCharsetHint, folderPath, descriptorNodeId, errors);
        ExtractedText bodyHtmlText = extractHtmlText(bodyHtml, folderPath, descriptorNodeId, errors);

        String parseStatus = errors.isEmpty() ? "OK" : "PARTIAL";
        return new ExtractedMail(
                folderId,
                folderPath,
                descriptorNodeId,
                internetMessageId,
                subject,
                senderName,
                senderEmail,
                recipients,
                cc,
                sentAt,
                receivedAt,
                bodyText,
                bodyHtml,
                bodyHtmlText,
                parseStatus,
                errors
        );
    }

    private ExtractedText extractText(String fieldName, PSTMessage message, int propertyId,
                                      SafePstFieldExtractor.ThrowingSupplier<String> getter,
                                      String charsetHint, String folderPath, Long descriptorNodeId,
                                      List<IndexFieldError> errors) {
        ExtractedText text = textExtractor.extract(message, propertyId, getter, charsetHint);
        if (text.hasError()) {
            errors.add(new IndexFieldError(folderPath, descriptorNodeId, "field", fieldName, text.errorType(), text.errorMessage()));
        }
        return text;
    }

    private ExtractedText extractHtmlText(ExtractedText html, String folderPath, Long descriptorNodeId, List<IndexFieldError> errors) {
        if (html.text() == null) {
            return new ExtractedText(null, html.status(), html.source(), html.errorType(), html.errorMessage());
        }
        try {
            String htmlText = HtmlTextExtractor.toText(html.text());
            TextRecoveryStatus status = htmlText == null || htmlText.isEmpty() ? TextRecoveryStatus.NULL : html.status();
            return new ExtractedText(htmlText, status, html.source(), null, null);
        } catch (Exception e) {
            String message = safeMessage(e);
            errors.add(new IndexFieldError(folderPath, descriptorNodeId, "field", "body_html_text",
                    e.getClass().getSimpleName(), message));
            return ExtractedText.error(e.getClass().getSimpleName(), message);
        }
    }

    private String safeString(String fieldName, SafePstFieldExtractor.ThrowingSupplier<String> supplier,
                              String folderPath, Long descriptorNodeId, List<IndexFieldError> errors) {
        try {
            String value = supplier.get();
            return value == null || value.isEmpty() ? null : value;
        } catch (Exception e) {
            errors.add(new IndexFieldError(folderPath, descriptorNodeId, "field", fieldName,
                    e.getClass().getSimpleName(), safeMessage(e)));
            return null;
        }
    }

    private String safeDate(String fieldName, SafePstFieldExtractor.ThrowingSupplier<Date> supplier,
                            String folderPath, Long descriptorNodeId, List<IndexFieldError> errors) {
        try {
            Date value = supplier.get();
            if (value == null) {
                return null;
            }
            return DATE_FORMATTER.format(value.toInstant().atZone(ZoneId.systemDefault()));
        } catch (Exception e) {
            errors.add(new IndexFieldError(folderPath, descriptorNodeId, "field", fieldName,
                    e.getClass().getSimpleName(), safeMessage(e)));
            return null;
        }
    }

    private Long safeLong(SafePstFieldExtractor.ThrowingSupplier<?> supplier) {
        try {
            Object value = supplier.get();
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer safeInteger(SafePstFieldExtractor.ThrowingSupplier<?> supplier) {
        try {
            Object value = supplier.get();
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "no message";
        }
        return message.replace("\r", " ").replace("\n", " ");
    }
}
