package com.legal.documentprocessing.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.legal.documentprocessing.composite.DocumentComposite;
import com.legal.documentprocessing.model.Document;
import com.legal.documentprocessing.model.DocumentSummary;
import com.legal.documentprocessing.service.DocumentService;
import com.legal.documentprocessing.service.DocumentService.UnsupportedDocumentTypeException;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class DocumentController {

    private static final Logger logger = LoggerFactory.getLogger(DocumentController.class);
    private final DocumentService documentService;

    @Autowired
    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            logger.warn("Upload request received with null or empty file");
            Map<String, String> error = new HashMap<>();
            error.put("message", "File is required");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            logger.info("Uploading document: {} ({} bytes, Content-Type: {})",
                file.getOriginalFilename(), file.getSize(), file.getContentType());
            Document document = documentService.uploadDocument(file);
            logger.info("Document uploaded successfully: {} with status: {}",
                document.getId(), document.getStatus());
            return ResponseEntity.status(HttpStatus.CREATED).body(DocumentSummary.from(document));
        } catch (UnsupportedDocumentTypeException e) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(Map.of("message", e.getMessage()));
        } catch (org.springframework.web.multipart.MaxUploadSizeExceededException e) {
            logger.error("File size exceeds maximum allowed size: {}", file.getOriginalFilename(), e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "File size exceeds 50MB limit");
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
        } catch (IOException e) {
            logger.error("IO error uploading document: {}", file.getOriginalFilename(), e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "Failed to read file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        } catch (Exception e) {
            logger.error("Unexpected error uploading document: {}", file.getOriginalFilename(), e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "Upload failed: " + e.getMessage());
            error.put("error", e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping
    public ResponseEntity<List<DocumentSummary>> getAllDocuments() {
        return ResponseEntity.ok(documentService.getAllDocuments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentDetail> getDocumentById(@PathVariable @NonNull String id) {
        try {
            Document document = documentService.getDocumentById(id);
            return ResponseEntity.ok(DocumentDetail.from(document));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<DocumentSummary>> getDocumentsByStatus(@PathVariable String status) {
        try {
            Document.DocumentStatus docStatus = Document.DocumentStatus.valueOf(status.toUpperCase());
            return ResponseEntity.ok(documentService.getDocumentsByStatus(docStatus));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<DocumentSummary>> getDocumentsByCategory(@PathVariable String category) {
        try {
            Document.DocumentCategory docCategory = Document.DocumentCategory.valueOf(category.toUpperCase());
            return ResponseEntity.ok(documentService.getDocumentsByCategory(docCategory));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}/structure")
    public ResponseEntity<DocumentComposite> getDocumentStructure(@PathVariable @NonNull String id) {
        try {
            DocumentComposite structure = documentService.getDocumentStructure(id);
            return ResponseEntity.ok(structure);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<?> processDocument(@PathVariable @NonNull String id) {
        try {
            logger.info("Processing document via manual trigger: {}", id);
            Document processed = documentService.processDocument(id);
            return ResponseEntity.ok(DocumentDetail.from(processed));
        } catch (IllegalStateException e) {
            logger.warn("Invalid state for processing document {}: {}", id, e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (RuntimeException e) {
            logger.error("Error processing document: {}", id, e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "Failed to process document: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    public record DocumentDetail(@JsonUnwrapped DocumentSummary summary, String contentText) {
        private static DocumentDetail from(Document document) {
            return new DocumentDetail(DocumentSummary.from(document), document.getContentText());
        }
    }
}
