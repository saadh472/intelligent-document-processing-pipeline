package com.legal.documentprocessing.validation;

import com.legal.documentprocessing.model.Document;
import com.legal.documentprocessing.model.ValidationResult;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Chain of Responsibility: Format validation handler
 * Validates file format, size, and type
 */
@Component
public class FormatValidationHandler extends AbstractValidationHandler {

    private static final List<String> ALLOWED_TYPES = Arrays.asList(
        "pdf", "doc", "docx", "txt", "jpg", "jpeg", "png"
    );
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    public static boolean isAllowedFileType(String fileType) {
        return fileType != null && ALLOWED_TYPES.contains(fileType.toLowerCase(Locale.ROOT));
    }

    @Override
    protected ValidationResult doValidate(Document document) {
        ValidationResult result = new ValidationResult();
        result.setDocumentId(document.getId());
        result.setValidatedBy(getHandlerName());

        // Check file type
        if (document.getFileType() == null || document.getFileType().isEmpty()) {
            result.addError("File type is missing");
            return result;
        }

        String fileType = document.getFileType();
        if (!isAllowedFileType(fileType)) {
            result.addError("File type '" + fileType + "' is not allowed. Allowed types: " + ALLOWED_TYPES);
            return result;
        }

        // Check file size
        if (document.getFileSize() == null || document.getFileSize() <= 0) {
            result.addError("File size is invalid");
            return result;
        }

        if (document.getFileSize() > MAX_FILE_SIZE) {
            result.addError("File size (" + document.getFileSize() + " bytes) exceeds maximum allowed size (" + MAX_FILE_SIZE + " bytes)");
            return result;
        }

        // Check content
        if (document.getContent() == null || document.getContent().length == 0) {
            result.addError("File content is empty");
            return result;
        }

        result.addWarning("Format validation passed");
        return result;
    }

    @Override
    protected String getHandlerName() {
        return "FormatValidationHandler";
    }
}
