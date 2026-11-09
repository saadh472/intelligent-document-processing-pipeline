package com.legal.documentprocessing.validation;

import com.legal.documentprocessing.model.Document;
import com.legal.documentprocessing.model.ValidationResult;

/**
 * Chain of Responsibility Pattern: Abstract base handler
 * Implements common chain logic
 */
public abstract class AbstractValidationHandler implements IValidationHandler {

    protected IValidationHandler nextHandler;

    @Override
    public IValidationHandler setNext(IValidationHandler handler) {
        this.nextHandler = handler;
        return handler;
    }

    @Override
    public ValidationResult validate(Document document) {
        ValidationResult result = doValidate(document);

        // If validation failed, return immediately
        if (!result.isValid()) {
            return result;
        }

        // If validation passed and there's a next handler, continue chain
        if (nextHandler != null) {
            ValidationResult nextResult = nextHandler.validate(document);
            // Merge results
            if (!nextResult.isValid()) {
                result.setValid(false);
            }
            result.getErrors().addAll(nextResult.getErrors());
            result.getWarnings().addAll(nextResult.getWarnings());
        }

        return result;
    }

    /**
     * Subclasses implement specific validation logic
     */
    protected abstract ValidationResult doValidate(Document document);

    /**
     * Get handler name for logging
     */
    protected abstract String getHandlerName();
}
