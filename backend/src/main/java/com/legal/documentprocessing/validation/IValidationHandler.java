package com.legal.documentprocessing.validation;

import com.legal.documentprocessing.model.Document;
import com.legal.documentprocessing.model.ValidationResult;

/**
 * Chain of Responsibility Pattern: Base interface for validation handlers
 * Each handler processes or passes to next in chain
 */
public interface IValidationHandler {
    /**
     * Sets the next handler in the chain
     */
    IValidationHandler setNext(IValidationHandler handler);

    /**
     * Validates the document
     * If validation fails, returns result immediately
     * If validation passes, calls next handler (if exists)
     */
    ValidationResult validate(Document document);
}
