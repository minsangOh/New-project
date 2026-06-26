package com.example.pstarchive.encoding;

import com.example.pstarchive.pst.SafePstFieldExtractor.ThrowingSupplier;
import com.pff.PSTObject;

import java.util.Optional;

public class EncodingAwareTextExtractor {
    public static final int PROP_SUBJECT = 0x0037;
    public static final int PROP_SENDER_NAME = 0x0c1a;
    public static final int PROP_DISPLAY_TO = 0x0e04;
    public static final int PROP_DISPLAY_CC = 0x0e03;
    public static final int PROP_BODY = 0x1000;
    public static final int PROP_HTML_BODY = 0x1013;
    public static final int PROP_FOLDER_NAME = 0x3001;
    public static final int PROP_MESSAGE_CODEPAGE = 0x3FFD;
    public static final int PROP_INTERNET_CODEPAGE = 0x3FDE;

    private final PstRawPropertyAccessor rawAccessor;
    private final BestEffortTextDecoder decoder;

    public EncodingAwareTextExtractor() {
        this(new PstRawPropertyAccessor(), new BestEffortTextDecoder());
    }

    public EncodingAwareTextExtractor(PstRawPropertyAccessor rawAccessor, BestEffortTextDecoder decoder) {
        this.rawAccessor = rawAccessor;
        this.decoder = decoder;
    }

    public ExtractedText extract(PSTObject object, int propertyId, ThrowingSupplier<String> getter, String charsetHint) {
        String getterText = null;
        Exception getterError = null;
        try {
            getterText = getter.get();
        } catch (Exception e) {
            getterError = e;
        }

        Optional<byte[]> rawBytes = rawAccessor.rawBytes(object, propertyId);
        if ((getterText == null || getterText.isEmpty()) && rawBytes.isEmpty()) {
            if (getterError != null) {
                return ExtractedText.error(getterError.getClass().getSimpleName(), safeMessage(getterError));
            }
            return ExtractedText.nullValue();
        }

        EncodingProbeResult probe = decoder.probe(getterText, rawBytes.orElse(null), charsetHint);
        TextRecoveryStatus status = TextRecoveryStatus.fromProbeStatus(probe.decodeStatus());
        String bestText = normalizeStoredText(probe.decodedBestText());
        if (bestText == null && getterError != null) {
            return ExtractedText.error(getterError.getClass().getSimpleName(), safeMessage(getterError));
        }
        return new ExtractedText(bestText, status, probe.bestLabel(), null, null);
    }

    public Optional<String> messageCodepageHint(PSTObject object) {
        return rawAccessor.intValue(object, PROP_MESSAGE_CODEPAGE).map(this::codepageToCharsetName);
    }

    public Optional<String> internetCodepageHint(PSTObject object) {
        return rawAccessor.intValue(object, PROP_INTERNET_CODEPAGE).map(this::codepageToCharsetName);
    }

    private String normalizeStoredText(String text) {
        if (text == null || text.isEmpty() || "<unrecoverable>".equals(text)) {
            return null;
        }
        return text;
    }

    private String codepageToCharsetName(int codepage) {
        return switch (codepage) {
            case 65001 -> "UTF-8";
            case 949, 51949 -> "MS949";
            case 5601 -> "EUC-KR";
            default -> "cp" + codepage;
        };
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "no message";
        }
        return message.replace("\r", " ").replace("\n", " ");
    }
}
