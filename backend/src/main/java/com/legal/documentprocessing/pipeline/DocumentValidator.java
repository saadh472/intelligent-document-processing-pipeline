package com.legal.documentprocessing.pipeline;

import com.legal.documentprocessing.model.Document;
import com.legal.documentprocessing.model.ProcessingEvent;
import com.legal.documentprocessing.model.ValidationResult;
import com.legal.documentprocessing.observer.ProcessingEventNotifier;
import com.legal.documentprocessing.validation.IValidationHandler;
import com.legal.documentprocessing.validation.ValidationChainBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Pipes and Filters: Validator Filter
 * Uses Chain of Responsibility for validation
 * Input: Extracted document → Output: Validated document
 */
@Component
public class DocumentValidator implements PipelineFilter {

    private final ValidationChainBuilder validationChainBuilder;
    private KafkaTemplate<String, Document> kafkaTemplate;
    private final ProcessingEventNotifier eventNotifier;
    private PipelineFilter next;

    @Autowired(required = false)
    public void setKafkaTemplate(@Qualifier("documentKafkaTemplate") KafkaTemplate<String, Document> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Autowired
    public DocumentValidator(ValidationChainBuilder validationChainBuilder,
                            ProcessingEventNotifier eventNotifier) {
        this.validationChainBuilder = validationChainBuilder;
        this.eventNotifier = eventNotifier;
    }

    @Override
    public Document process(Document document) {
        try {
            eventNotifier.notifyObservers(ProcessingEvent.create(
                document.getId(),
                "VALIDATED",
                "Starting validation",
                60,
                "Validator"
            ));

            // Build validation chain and validate
            IValidationHandler validationChain = validationChainBuilder.buildChain();
            ValidationResult result = validationChain.validate(document);

            if (!result.isValid()) {
                document.setStatus(Document.DocumentStatus.ERROR);
                document.setErrorMessage("Validation failed: " + String.join(", ", result.getErrors()));

                eventNotifier.notifyObservers(ProcessingEvent.createError(
                    document.getId(),
                    "ERROR",
                    "Validation failed",
                    "Validator",
                    document.getErrorMessage()
                ));

                return document;
            }

            document.setStatus(Document.DocumentStatus.VALIDATED);

            String message = "Validation complete";
            if (!result.getWarnings().isEmpty()) {
                message += " (warnings: " + result.getWarnings().size() + ")";
            }

            eventNotifier.notifyObservers(ProcessingEvent.create(
                document.getId(),
                "VALIDATED",
                message,
                80,
                "Validator"
            ));

            // Send to next stage via Kafka (if available)
            if (kafkaTemplate != null) {
                try {
                    String docId = document.getId() != null ? document.getId() : "";
                    kafkaTemplate.send("document-validated", Objects.requireNonNull(docId), document);
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
            document.setErrorMessage("Validation failed: " + e.getMessage());

            eventNotifier.notifyObservers(ProcessingEvent.createError(
                document.getId(),
                "ERROR",
                "Validation failed",
                "Validator",
                e.getMessage()
            ));

            return document;
        }
    }

    @KafkaListener(topics = "document-extracted", groupId = "document-processing-group")
    public void consume(Document document) {
        process(document);
    }

    @Override
    public String getFilterName() {
        return "DocumentValidator";
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
