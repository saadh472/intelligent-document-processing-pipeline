package com.legal.documentprocessing.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.legal.documentprocessing.composite.DocumentComposite;
import com.legal.documentprocessing.composite.DocumentStructureBuilder;
import com.legal.documentprocessing.model.Document;
import com.legal.documentprocessing.model.ProcessingEvent;
import com.legal.documentprocessing.observer.ProcessingEventNotifier;
import com.legal.documentprocessing.pipeline.DocumentCategorizer;
import com.legal.documentprocessing.pipeline.DocumentExtractor;
import com.legal.documentprocessing.pipeline.DocumentTokenizer;
import com.legal.documentprocessing.pipeline.DocumentValidator;
import com.legal.documentprocessing.pipeline.PipelineOutput;
import com.legal.documentprocessing.repository.DocumentRepository;

@Service
@SuppressWarnings("unused")
public class DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DocumentTokenizer documentTokenizer;
    private final DocumentExtractor documentExtractor; // Used in pipeline chain setup via setNext()
    private final DocumentValidator documentValidator; // Used in pipeline chain setup via setNext()
    private final DocumentCategorizer documentCategorizer; // Used in pipeline chain setup via setNext()
    private final PipelineOutput pipelineOutput; // Used in pipeline chain setup via setNext()
    private KafkaTemplate<String, Object> kafkaTemplate;
    private final ProcessingEventNotifier eventNotifier;
    private final DocumentStructureBuilder structureBuilder;

    @Autowired(required = false)
    public void setKafkaTemplate(@Qualifier("documentInputKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Autowired
    public DocumentService(DocumentRepository documentRepository,
                          DocumentTokenizer documentTokenizer,
                          DocumentExtractor documentExtractor,
                          DocumentValidator documentValidator,
                          DocumentCategorizer documentCategorizer,
                          PipelineOutput pipelineOutput,
                          ProcessingEventNotifier eventNotifier,
                          DocumentStructureBuilder structureBuilder) {
        this.documentRepository = documentRepository;
        this.documentTokenizer = documentTokenizer;
        this.documentExtractor = documentExtractor;
        this.documentValidator = documentValidator;
        this.documentCategorizer = documentCategorizer;
        this.pipelineOutput = pipelineOutput;
        this.eventNotifier = eventNotifier;
        this.structureBuilder = structureBuilder;

        // Set up pipeline chain manually for direct processing
        documentTokenizer.setNext(documentExtractor);
        documentExtractor.setNext(documentValidator);
        documentValidator.setNext(documentCategorizer);
        documentCategorizer.setNext(pipelineOutput);
    }

    public Document uploadDocument(MultipartFile file) throws IOException {
        logger.info("Processing upload for file: {}", file.getOriginalFilename());

        String fileExtension = getFileExtension(file.getOriginalFilename());

        // Validate file size (50MB limit)
        long maxSize = 50 * 1024 * 1024; // 50MB
        if (file.getSize() > maxSize) {
            throw new IOException("File size exceeds 50MB limit");
        }

        Document document = Document.builder()
                .fileName(file.getOriginalFilename())
                .fileType(fileExtension)
                .mimeType(file.getContentType())
                .fileSize(file.getSize())
                .content(file.getBytes())
                .status(Document.DocumentStatus.UPLOADED)
                .uploadedAt(LocalDateTime.now())
                .build();

        final Document savedDocument;
        try {
            // JPA repositories don't return null, but the type system doesn't guarantee it
            @SuppressWarnings("null")
            Document saved = documentRepository.save(document);
            savedDocument = saved;
            logger.info("Document saved to database: {}", savedDocument.getId());
        } catch (Exception e) {
            logger.error("Failed to save document to database", e);
            throw new IOException("Failed to save document: " + e.getMessage(), e);
        }

        // Notify observers (non-blocking)
        try {
            eventNotifier.notifyObservers(ProcessingEvent.create(
                savedDocument.getId(),
                "UPLOADED",
                "Document uploaded: " + savedDocument.getFileName(),
                0,
                "Upload"
            ));
        } catch (Exception e) {
            logger.warn("Failed to notify observers", e);
            // Don't fail upload if notification fails
        }

        // Send to pipeline via Kafka (non-blocking, don't fail if Kafka is down)
        // Only send document ID to Kafka, not the full document (to avoid serialization issues)
        if (kafkaTemplate != null) {
            try {
                final String documentId = savedDocument.getId() != null ? savedDocument.getId() : "";
                // Create a simple DTO for Kafka instead of sending the full Document entity
                Map<String, Object> kafkaMessage = new HashMap<>();
                kafkaMessage.put("documentId", documentId);
                kafkaMessage.put("fileName", savedDocument.getFileName());
                kafkaMessage.put("status", savedDocument.getStatus().toString());

                CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send("document-input", Objects.requireNonNull(documentId), kafkaMessage);

                future.whenComplete((result, ex) -> {
                    if (ex == null) {
                        logger.info("Document sent to Kafka: {}", documentId);
                    } else {
                        logger.warn("Failed to send document to Kafka: {}", documentId, ex);
                        // Don't fail upload if Kafka is unavailable
                    }
                });
            } catch (Exception e) {
                logger.warn("Kafka send failed (continuing anyway): {}", e.getMessage(), e);
                // Don't fail upload if Kafka is unavailable
            }
        } else {
            logger.debug("Kafka not configured, skipping message send");
        }

        return savedDocument;
    }

    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }

    public Document getDocumentById(@NonNull String id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found: " + id));
    }

    public List<Document> getDocumentsByStatus(Document.DocumentStatus status) {
        return documentRepository.findByStatus(status);
    }

    public List<Document> getDocumentsByCategory(Document.DocumentCategory category) {
        return documentRepository.findByCategory(category);
    }

    public DocumentComposite getDocumentStructure(@NonNull String id) {
        Document document = getDocumentById(id);
        return structureBuilder.buildStructure(document);
    }

    /**
     * Manually trigger pipeline processing for a document
     * This is useful when Kafka is not available or for manual processing
     */
    public Document processDocument(@NonNull String id) {
        Document document = getDocumentById(id);

        // Only process if document is in UPLOADED status
        if (document.getStatus() != Document.DocumentStatus.UPLOADED) {
            throw new IllegalStateException("Document must be in UPLOADED status to start processing. Current status: " + document.getStatus());
        }

        logger.info("Starting manual pipeline processing for document: {}", id);

        try {
            // Process through the pipeline chain (Tokenizer -> Extractor -> Validator -> Categorizer -> Output)
            Document processed = documentTokenizer.process(document);

            // Save the processed document
            @SuppressWarnings("null")
            Document saved = documentRepository.save(processed);
            logger.info("Pipeline processing completed for document: {} with status: {}", id, saved.getStatus());

            return saved;
        } catch (Exception e) {
            logger.error("Error processing document: {}", id, e);
            document.setStatus(Document.DocumentStatus.ERROR);
            document.setErrorMessage("Processing failed: " + e.getMessage());
            documentRepository.save(document);
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "unknown";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
