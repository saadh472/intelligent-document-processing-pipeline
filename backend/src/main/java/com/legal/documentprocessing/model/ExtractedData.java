package com.legal.documentprocessing.model;

import java.time.LocalDateTime;
import java.util.Map;

public class ExtractedData {
    private String documentId;
    private String extractionStrategy;
    private Map<String, String> metadata;
    private Map<String, Object> extractedFields;
    private String fullText;
    private LocalDateTime extractionTime;
    private double confidenceScore;

    // Constructors
    public ExtractedData() {
    }

    public ExtractedData(String documentId, String extractionStrategy, Map<String, String> metadata,
                        Map<String, Object> extractedFields, String fullText, LocalDateTime extractionTime,
                        double confidenceScore) {
        this.documentId = documentId;
        this.extractionStrategy = extractionStrategy;
        this.metadata = metadata;
        this.extractedFields = extractedFields;
        this.fullText = fullText;
        this.extractionTime = extractionTime;
        this.confidenceScore = confidenceScore;
    }

    // Builder pattern
    public static ExtractedDataBuilder builder() {
        return new ExtractedDataBuilder();
    }

    public static class ExtractedDataBuilder {
        private String documentId;
        private String extractionStrategy;
        private Map<String, String> metadata;
        private Map<String, Object> extractedFields;
        private String fullText;
        private LocalDateTime extractionTime;
        private double confidenceScore;

        public ExtractedDataBuilder documentId(String documentId) { this.documentId = documentId; return this; }
        public ExtractedDataBuilder extractionStrategy(String extractionStrategy) { this.extractionStrategy = extractionStrategy; return this; }
        public ExtractedDataBuilder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }
        public ExtractedDataBuilder extractedFields(Map<String, Object> extractedFields) { this.extractedFields = extractedFields; return this; }
        public ExtractedDataBuilder fullText(String fullText) { this.fullText = fullText; return this; }
        public ExtractedDataBuilder extractionTime(LocalDateTime extractionTime) { this.extractionTime = extractionTime; return this; }
        public ExtractedDataBuilder confidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; return this; }

        public ExtractedData build() {
            return new ExtractedData(documentId, extractionStrategy, metadata, extractedFields,
                                    fullText, extractionTime, confidenceScore);
        }
    }

    // Getters and Setters
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getExtractionStrategy() { return extractionStrategy; }
    public void setExtractionStrategy(String extractionStrategy) { this.extractionStrategy = extractionStrategy; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    public Map<String, Object> getExtractedFields() { return extractedFields; }
    public void setExtractedFields(Map<String, Object> extractedFields) { this.extractedFields = extractedFields; }

    public String getFullText() { return fullText; }
    public void setFullText(String fullText) { this.fullText = fullText; }

    public LocalDateTime getExtractionTime() { return extractionTime; }
    public void setExtractionTime(LocalDateTime extractionTime) { this.extractionTime = extractionTime; }

    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }
}
