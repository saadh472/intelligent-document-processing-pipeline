package com.legal.documentprocessing.validation;

import com.legal.documentprocessing.model.Document;
import com.legal.documentprocessing.model.ValidationResult;
import org.springframework.stereotype.Component;

/**
 * Chain of Responsibility: Content validation handler
 * Validates document content quality and structure
 */
@Component
public class ContentValidationHandler extends AbstractValidationHandler {

    private static final int MIN_CONTENT_LENGTH = 10;
    private static final int MIN_TEXT_LENGTH = 5;

    @Override
    protected ValidationResult doValidate(Document document) {
        ValidationResult result = new ValidationResult();
        result.setDocumentId(document.getId());
        result.setValidatedBy(getHandlerName());

        // Check if content exists
        if (document.getContent() == null || document.getContent().length < MIN_CONTENT_LENGTH) {
            result.addError("Document content is too short or empty");
            return result;
        }

        // Check text content if available
        if (document.getContentText() != null) {
            String text = document.getContentText().trim();
            if (text.length() < MIN_TEXT_LENGTH) {
                result.addWarning("Document text content is very short (" + text.length() + " characters)");
            }

            // Check for suspicious patterns (e.g., all same character)
            if (isSuspiciousContent(text)) {
                result.addWarning("Document content appears suspicious or corrupted");
            }
        }

        // Check for binary content validity
        if (document.getFileType().equalsIgnoreCase("pdf") ||
            document.getFileType().equalsIgnoreCase("doc") ||
            document.getFileType().equalsIgnoreCase("docx")) {
            if (!isValidBinaryContent(document.getContent())) {
                result.addError("Binary document content appears corrupted");
                return result;
            }
        }

        result.addWarning("Content validation passed");
        return result;
    }

    private boolean isSuspiciousContent(String text) {
        if (text.length() < 10) return false;

        // Check if all characters are the same
        char firstChar = text.charAt(0);
        boolean allSame = true;
        for (char c : text.toCharArray()) {
            if (c != firstChar && !Character.isWhitespace(c)) {
                allSame = false;
                break;
            }
        }
        return allSame;
    }

    private boolean isValidBinaryContent(byte[] content) {
        // Basic validation: check for PDF magic number or DOC magic bytes
        if (content.length < 4) return false;

        // PDF starts with %PDF
        if (content[0] == 0x25 && content[1] == 0x50 && content[2] == 0x44 && content[3] == 0x46) {
            return true;
        }

        // DOC/DOCX have specific magic bytes
        // DOC: D0 CF 11 E0
        if (content[0] == (byte)0xD0 && content[1] == (byte)0xCF &&
            content[2] == 0x11 && content[3] == (byte)0xE0) {
            return true;
        }

        // DOCX is a ZIP file (PK)
        if (content[0] == 0x50 && content[1] == 0x4B) {
            return true;
        }

        // For text files, allow any content
        return true;
    }

    @Override
    protected String getHandlerName() {
        return "ContentValidationHandler";
    }
}
