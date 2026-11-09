package com.legal.documentprocessing.validation;

import com.legal.documentprocessing.model.Document;
import com.legal.documentprocessing.model.ValidationResult;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Chain of Responsibility: Compliance validation handler
 * Validates legal compliance requirements
 */
@Component
public class ComplianceValidationHandler extends AbstractValidationHandler {

    // Patterns for sensitive information detection
    private static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b");

    private static final List<String> RESTRICTED_TERMS = Arrays.asList(
        "confidential", "privileged", "attorney-client"
    );

    @Override
    protected ValidationResult doValidate(Document document) {
        ValidationResult result = new ValidationResult();
        result.setDocumentId(document.getId());
        result.setValidatedBy(getHandlerName());

        String content = document.getContentText();
        if (content == null || content.isEmpty()) {
            result.addWarning("Cannot perform compliance check - no text content available");
            return result;
        }

        final String contentLower = content.toLowerCase();

        // Check for SSN
        if (SSN_PATTERN.matcher(contentLower).find()) {
            result.addWarning("Document may contain Social Security Numbers - ensure proper handling");
        }

        // Check for credit card numbers
        if (CREDIT_CARD_PATTERN.matcher(contentLower).find()) {
            result.addWarning("Document may contain credit card numbers - ensure PCI compliance");
        }

        // Check for restricted/confidential terms
        boolean hasRestrictedTerms = RESTRICTED_TERMS.stream()
                .anyMatch(term -> contentLower.contains(term.toLowerCase()));

        if (hasRestrictedTerms) {
            result.addWarning("Document contains restricted/confidential terms - verify access permissions");
        }

        // Check minimum content requirements for legal documents
        if (contentLower.length() < 100) {
            result.addWarning("Document content is very short - may not meet legal document standards");
        }

        // Check for required legal document elements
        boolean hasDate = contentLower.matches(".*\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\b.*");
        boolean hasParties = contentLower.matches(".*\\b(plaintiff|defendant|party|parties)\\b.*");

        if (!hasDate && !hasParties) {
            result.addWarning("Document may be missing standard legal document elements (dates, parties)");
        }

        result.addWarning("Compliance validation completed");
        return result;
    }

    @Override
    protected String getHandlerName() {
        return "ComplianceValidationHandler";
    }
}
