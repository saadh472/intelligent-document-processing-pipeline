package com.legal.documentprocessing.pipeline;

import com.legal.documentprocessing.model.Document;
import com.legal.documentprocessing.model.ExtractedData;
import com.legal.documentprocessing.model.ProcessingEvent;
import com.legal.documentprocessing.observer.ProcessingEventNotifier;
import com.legal.documentprocessing.strategy.ExtractionStrategyFactory;
import com.legal.documentprocessing.strategy.IExtractionStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Pipes and Filters: Extractor Filter
 * Uses Strategy Pattern to extract structured data
 * Input: Tokenized document → Output: Document with extracted data
 */
@Component
public class DocumentExtractor implements PipelineFilter {

    private final ExtractionStrategyFactory strategyFactory;
    private KafkaTemplate<String, Document> kafkaTemplate;
    private final ProcessingEventNotifier eventNotifier;
    private PipelineFilter next;

    @Autowired(required = false)
    public void setKafkaTemplate(@Qualifier("documentKafkaTemplate") KafkaTemplate<String, Document> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Autowired
    public DocumentExtractor(ExtractionStrategyFactory strategyFactory,
                            ProcessingEventNotifier eventNotifier) {
        this.strategyFactory = strategyFactory;
        this.eventNotifier = eventNotifier;
    }

    @Override
    public Document process(Document document) {
        try {
            eventNotifier.notifyObservers(ProcessingEvent.create(
                document.getId(),
                "EXTRACTED",
                "Starting extraction",
                30,
                "Extractor"
            ));

            // Select appropriate strategy based on document type
            IExtractionStrategy strategy = strategyFactory.getStrategy(document.getFileType());

            eventNotifier.notifyObservers(ProcessingEvent.create(
                document.getId(),
                "EXTRACTED",
                "Using extraction strategy: " + strategy.getStrategyType(),
                35,
                "Extractor"
            ));

            // Extract data using selected strategy
            String content = document.getContentText() != null ?
                document.getContentText() : new String(document.getContent());

            ExtractedData extractedData = strategy.extract(content);
            extractedData.setDocumentId(document.getId());

            // Store extracted data (in production, save to database)
            // For now, we'll store it in a field or process it

            document.setStatus(Document.DocumentStatus.EXTRACTED);

            eventNotifier.notifyObservers(ProcessingEvent.create(
                document.getId(),
                "EXTRACTED",
                "Extraction complete using " + strategy.getStrategyType() +
                " (confidence: " + extractedData.getConfidenceScore() + ")",
                50,
                "Extractor"
            ));

            // Send to next stage via Kafka (if available)
            if (kafkaTemplate != null) {
                try {
                    String docId = document.getId() != null ? document.getId() : "";
                    kafkaTemplate.send("document-extracted", Objects.requireNonNull(docId), document);
                } catch (Exception e) {
                    System.err.println("Kafka send failed (continuing): " + e.getMessage());
                }
            }

            // Process next filter if exists
            if (next != null) {
                return next.process(document);
            }

            return document;

        } catch (Exception e) {
            document.setStatus(Document.DocumentStatus.ERROR);
            document.setErrorMessage("Extraction failed: " + e.getMessage());

            eventNotifier.notifyObservers(ProcessingEvent.createError(
                document.getId(),
                "ERROR",
                "Extraction failed",
                "Extractor",
                e.getMessage()
            ));

            return document;
        }
    }

    @KafkaListener(topics = "document-tokenized", groupId = "document-processing-group")
    public void consume(Document document) {
        process(document);
    }

    @Override
    public String getFilterName() {
        return "DocumentExtractor";
    }

    @Override
    public PipelineFilter getNext() {
        return next;
    }

    @Override
    public void setNext(PipelineFilter next) {
        this.next = next;
    }
}
