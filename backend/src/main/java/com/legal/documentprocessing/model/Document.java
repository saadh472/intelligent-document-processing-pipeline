package com.legal.documentprocessing.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String fileName;
    private String fileType;
    private String mimeType;
    private Long fileSize;

    @Lob
    @Column(columnDefinition = "BLOB")
    private byte[] content;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String contentText;

    @Enumerated(EnumType.STRING)
    private DocumentStatus status;

    @Enumerated(EnumType.STRING)
    private DocumentCategory category;

    private LocalDateTime uploadedAt;
    private LocalDateTime processedAt;

    private String errorMessage;

    // Constructors
    public Document() {
    }

    public Document(String id, String fileName, String fileType, String mimeType, Long fileSize,
                   byte[] content, String contentText, DocumentStatus status, DocumentCategory category,
                   LocalDateTime uploadedAt, LocalDateTime processedAt, String errorMessage) {
        this.id = id;
        this.fileName = fileName;
        this.fileType = fileType;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.content = content;
        this.contentText = contentText;
        this.status = status;
        this.category = category;
        this.uploadedAt = uploadedAt;
        this.processedAt = processedAt;
        this.errorMessage = errorMessage;
    }

    // Builder pattern
    public static DocumentBuilder builder() {
        return new DocumentBuilder();
    }

    public static class DocumentBuilder {
        private String id;
        private String fileName;
        private String fileType;
        private String mimeType;
        private Long fileSize;
        private byte[] content;
        private String contentText;
        private DocumentStatus status;
        private DocumentCategory category;
        private LocalDateTime uploadedAt;
        private LocalDateTime processedAt;
        private String errorMessage;

        public DocumentBuilder id(String id) { this.id = id; return this; }
        public DocumentBuilder fileName(String fileName) { this.fileName = fileName; return this; }
        public DocumentBuilder fileType(String fileType) { this.fileType = fileType; return this; }
        public DocumentBuilder mimeType(String mimeType) { this.mimeType = mimeType; return this; }
        public DocumentBuilder fileSize(Long fileSize) { this.fileSize = fileSize; return this; }
        public DocumentBuilder content(byte[] content) { this.content = content; return this; }
        public DocumentBuilder contentText(String contentText) { this.contentText = contentText; return this; }
        public DocumentBuilder status(DocumentStatus status) { this.status = status; return this; }
        public DocumentBuilder category(DocumentCategory category) { this.category = category; return this; }
        public DocumentBuilder uploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; return this; }
        public DocumentBuilder processedAt(LocalDateTime processedAt) { this.processedAt = processedAt; return this; }
        public DocumentBuilder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

        public Document build() {
            return new Document(id, fileName, fileType, mimeType, fileSize, content, contentText,
                              status, category, uploadedAt, processedAt, errorMessage);
        }
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }

    public String getContentText() { return contentText; }
    public void setContentText(String contentText) { this.contentText = contentText; }

    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }

    public DocumentCategory getCategory() { return category; }
    public void setCategory(DocumentCategory category) { this.category = category; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public enum DocumentStatus {
        UPLOADED, TOKENIZED, EXTRACTED, VALIDATED, CATEGORIZED, COMPLETED, ERROR
    }

    public enum DocumentCategory {
        CONTRACT, MOTION, BRIEF, EVIDENCE, CORRESPONDENCE, OTHER
    }
}
