package com.legal.documentprocessing.pipeline;

import com.legal.documentprocessing.model.Document;
import com.legal.documentprocessing.model.ProcessingEvent;
import com.legal.documentprocessing.observer.ProcessingEventNotifier;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Pipes and Filters: Tokenizer Filter
 * Extracts text content from binary documents
 * Input: Raw document → Output: Tokenized document with text content
 */
@Component
public class DocumentTokenizer implements PipelineFilter {

    private final Tika tika = new Tika();
    private KafkaTemplate<String, Document> kafkaTemplate;
    private final ProcessingEventNotifier eventNotifier;
    private PipelineFilter next;

    @Autowired(required = false)
    public void setKafkaTemplate(@Qualifier("documentKafkaTemplate") KafkaTemplate<String, Document> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Autowired
    public DocumentTokenizer(ProcessingEventNotifier eventNotifier) {
        this.eventNotifier = eventNotifier;
    }

    @Override
    public Document process(Document document) {
        try {
            eventNotifier.notifyObservers(ProcessingEvent.create(
                document.getId(),
                "TOKENIZED",
                "Starting tokenization",
                10,
                "Tokenizer"
            ));

            // Extract text using Tika
            String extractedText;
            try {
                extractedText = tika.parseToString(new ByteArrayInputStream(document.getContent()));
            } catch (TikaException | IOException e) {
                throw new IOException("Tika parsing failed: " + e.getMessage(), e);
            }
            document.setContentText(extractedText);
            document.setStatus(Document.DocumentStatus.TOKENIZED);

            eventNotifier.notifyObservers(ProcessingEvent.create(
                document.getId(),
                "TOKENIZED",
                "Tokenization complete: " + extractedText.length() + " characters extracted",
                20,
                "Tokenizer"
            ));

            // Send to next stage via Kafka (if available)
            if (kafkaTemplate != null) {
                try {
                    String docId = document.getId() != null ? document.getId() : "";
                    kafkaTemplate.send("document-tokenized", Objects.requireNonNull(docId), document);
                } catch (Exception e) {
                    // Log but don't fail if Kafka is unavailable
                    System.err.println("Kafka send failed (continuing): " + e.getMessage());
                }
            }

            // Process next filter if exists
            if (next != null) {
                return next.process(document);
            }

            return document;

        } catch (IOException e) {
            document.setStatus(Document.DocumentStatus.ERROR);
            document.setErrorMessage("Tokenization failed: " + e.getMessage());

            eventNotifier.notifyObservers(ProcessingEvent.createError(
                document.getId(),
                "ERROR",
                "Tokenization failed",
                "Tokenizer",
                e.getMessage()
            ));

            return document;
        }
    }

    @KafkaListener(topics = "document-input", groupId = "document-processing-group",
                   autoStartup = "${spring.kafka.listener.auto-startup:false}")
    public void consume(Document document) {
        process(document);
    }

    @Override
    public String getFilterName() {
        return "DocumentTokenizer";
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
