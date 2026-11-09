package com.legal.documentprocessing.model;

import java.time.LocalDateTime;

public class ProcessingEvent {
    private String documentId;
    private String eventType; // UPLOADED, TOKENIZED, EXTRACTED, VALIDATED, CATEGORIZED, COMPLETED, ERROR
    private String message;
    private int progressPercentage;
    private LocalDateTime timestamp = LocalDateTime.now();
    private String stage; // Current pipeline stage
    private String errorMessage; // If eventType is ERROR

    // Constructors
    public ProcessingEvent() {
        this.timestamp = LocalDateTime.now();
    }

    public ProcessingEvent(String documentId, String eventType, String message, int progressPercentage,
                          LocalDateTime timestamp, String stage, String errorMessage) {
        this.documentId = documentId;
        this.eventType = eventType;
        this.message = message;
        this.progressPercentage = progressPercentage;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.stage = stage;
        this.errorMessage = errorMessage;
    }

    // Builder pattern
    public static ProcessingEventBuilder builder() {
        return new ProcessingEventBuilder();
    }

    public static class ProcessingEventBuilder {
        private String documentId;
        private String eventType;
        private String message;
        private int progressPercentage;
        private LocalDateTime timestamp = LocalDateTime.now();
        private String stage;
        private String errorMessage;

        public ProcessingEventBuilder documentId(String documentId) { this.documentId = documentId; return this; }
        public ProcessingEventBuilder eventType(String eventType) { this.eventType = eventType; return this; }
        public ProcessingEventBuilder message(String message) { this.message = message; return this; }
        public ProcessingEventBuilder progressPercentage(int progressPercentage) { this.progressPercentage = progressPercentage; return this; }
        public ProcessingEventBuilder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
        public ProcessingEventBuilder stage(String stage) { this.stage = stage; return this; }
        public ProcessingEventBuilder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

        public ProcessingEvent build() {
            return new ProcessingEvent(documentId, eventType, message, progressPercentage, timestamp, stage, errorMessage);
        }
    }

    // Getters and Setters
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public int getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(int progressPercentage) { this.progressPercentage = progressPercentage; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    // Convenience factory methods
    public static ProcessingEvent create(String documentId, String eventType, String message, int progressPercentage, String stage) {
        return ProcessingEvent.builder()
                .documentId(documentId)
                .eventType(eventType)
                .message(message)
                .progressPercentage(progressPercentage)
                .stage(stage)
                .build();
    }

    public static ProcessingEvent createError(String documentId, String eventType, String message, String stage, String errorMessage) {
        return ProcessingEvent.builder()
                .documentId(documentId)
                .eventType(eventType)
                .message(message)
                .progressPercentage(0)
                .stage(stage)
                .errorMessage(errorMessage)
                .build();
    }
}
