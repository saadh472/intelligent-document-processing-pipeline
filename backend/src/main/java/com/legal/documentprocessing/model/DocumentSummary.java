package com.legal.documentprocessing.model;

import java.time.LocalDateTime;

import com.legal.documentprocessing.model.Document.DocumentCategory;
import com.legal.documentprocessing.model.Document.DocumentStatus;

public record DocumentSummary(
        String id,
        String fileName,
        String fileType,
        String mimeType,
        Long fileSize,
        DocumentStatus status,
        DocumentCategory category,
        LocalDateTime uploadedAt,
        LocalDateTime processedAt,
        String errorMessage) {

    public static DocumentSummary from(Document document) {
        return new DocumentSummary(
                document.getId(), document.getFileName(), document.getFileType(), document.getMimeType(),
                document.getFileSize(), document.getStatus(), document.getCategory(), document.getUploadedAt(),
                document.getProcessedAt(), document.getErrorMessage());
    }
}
