package com.legal.documentprocessing.pipeline;

import com.legal.documentprocessing.model.Document;
import com.legal.documentprocessing.model.ProcessingEvent;
import com.legal.documentprocessing.observer.ProcessingEventNotifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Pipes and Filters: Output Filter (Final Stage)
 * Marks document as completed and notifies observers
 * Input: Categorized document → Output: Completed document
 */
@Component
public class PipelineOutput implements PipelineFilter {

    private final ProcessingEventNotifier eventNotifier;

    @Autowired
    public PipelineOutput(ProcessingEventNotifier eventNotifier) {
        this.eventNotifier = eventNotifier;
    }

    @Override
    public Document process(Document document) {
        document.setStatus(Document.DocumentStatus.COMPLETED);
        document.setProcessedAt(java.time.LocalDateTime.now());

        eventNotifier.notifyObservers(ProcessingEvent.create(
            document.getId(),
            "COMPLETED",
            "Document processing completed successfully. Category: " + document.getCategory(),
            100,
            "Output"
        ));

        return document;
    }

    @KafkaListener(topics = "document-categorized", groupId = "document-processing-group")
    public void consume(Document document) {
        process(document);
    }

    @Override
    public String getFilterName() {
        return "PipelineOutput";
    }

    @Override
    public PipelineFilter getNext() {
        return null; // This is the final stage
    }

    @Override
    public void setNext(PipelineFilter next) {
        // No next stage for output
    }
}
