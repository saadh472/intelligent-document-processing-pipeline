package com.legal.documentprocessing.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ValidationResult {
    private String documentId;
    private boolean isValid = true;
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private String validatedBy; // Handler that performed validation
    private LocalDateTime validationTime = LocalDateTime.now();

    // Constructors
    public ValidationResult() {
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.isValid = true;
        this.validationTime = LocalDateTime.now();
    }

    public ValidationResult(String documentId, boolean isValid, List<String> errors,
                           List<String> warnings, String validatedBy, LocalDateTime validationTime) {
        this.documentId = documentId;
        this.isValid = isValid;
        this.errors = errors != null ? errors : new ArrayList<>();
        this.warnings = warnings != null ? warnings : new ArrayList<>();
        this.validatedBy = validatedBy;
        this.validationTime = validationTime != null ? validationTime : LocalDateTime.now();
    }

    // Builder pattern
    public static ValidationResultBuilder builder() {
        return new ValidationResultBuilder();
    }

    public static class ValidationResultBuilder {
        private String documentId;
        private boolean isValid = true;
        private List<String> errors = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private String validatedBy;
        private LocalDateTime validationTime = LocalDateTime.now();

        public ValidationResultBuilder documentId(String documentId) { this.documentId = documentId; return this; }
        public ValidationResultBuilder isValid(boolean isValid) { this.isValid = isValid; return this; }
        public ValidationResultBuilder errors(List<String> errors) { this.errors = errors; return this; }
        public ValidationResultBuilder warnings(List<String> warnings) { this.warnings = warnings; return this; }
        public ValidationResultBuilder validatedBy(String validatedBy) { this.validatedBy = validatedBy; return this; }
        public ValidationResultBuilder validationTime(LocalDateTime validationTime) { this.validationTime = validationTime; return this; }

        public ValidationResult build() {
            return new ValidationResult(documentId, isValid, errors, warnings, validatedBy, validationTime);
        }
    }

    // Getters and Setters
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public boolean isValid() { return isValid; }
    public void setValid(boolean isValid) { this.isValid = isValid; }

    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }

    public String getValidatedBy() { return validatedBy; }
    public void setValidatedBy(String validatedBy) { this.validatedBy = validatedBy; }

    public LocalDateTime getValidationTime() { return validationTime; }
    public void setValidationTime(LocalDateTime validationTime) { this.validationTime = validationTime; }

    public void addError(String error) {
        this.errors.add(error);
        this.isValid = false;
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }
}
