package com.legal.documentprocessing.validation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Builder for creating validation chain
 * Sets up Chain of Responsibility in correct order
 */
@Component
public class ValidationChainBuilder {

    private final FormatValidationHandler formatHandler;
    private final ContentValidationHandler contentHandler;
    private final ComplianceValidationHandler complianceHandler;

    @Autowired
    public ValidationChainBuilder(
            FormatValidationHandler formatHandler,
            ContentValidationHandler contentHandler,
            ComplianceValidationHandler complianceHandler) {
        this.formatHandler = formatHandler;
        this.contentHandler = contentHandler;
        this.complianceHandler = complianceHandler;
    }

    /**
     * Builds the validation chain in order:
     * Format → Content → Compliance
     */
    public IValidationHandler buildChain() {
        formatHandler.setNext(contentHandler);
        contentHandler.setNext(complianceHandler);
        return formatHandler;
    }
}
