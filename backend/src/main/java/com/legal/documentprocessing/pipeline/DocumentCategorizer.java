package com.legal.documentprocessing.pipeline;

import com.legal.documentprocessing.model.Document;
import com.legal.documentprocessing.model.ProcessingEvent;
import com.legal.documentprocessing.observer.ProcessingEventNotifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Pipes and Filters: Categorizer Filter
 * Categorizes documents based on content analysis
 * Input: Validated document → Output: Categorized document
 */
@Component
public class DocumentCategorizer implements PipelineFilter {

    private KafkaTemplate<String, Document> kafkaTemplate;
    private final ProcessingEventNotifier eventNotifier;
    private PipelineFilter next;

    // Patterns for document categorization
    private static final Pattern CONTRACT_PATTERN = Pattern.compile(
        "\\b(contract|agreement|terms|conditions|party|parties)\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MOTION_PATTERN = Pattern.compile(
        "\\b(motion|move|request|petition|application)\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BRIEF_PATTERN = Pattern.compile(
        "\\b(brief|argument|legal|precedent|case law|statute)\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EVIDENCE_PATTERN = Pattern.compile(
        "\\b(evidence|exhibit|document|affidavit|deposition|testimony)\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CORRESPONDENCE_PATTERN = Pattern.compile(
        "\\b(letter|email|correspondence|communication|memo|memorandum)\\b", Pattern.CASE_INSENSITIVE
    );

    @Autowired(required = false)
    public void setKafkaTemplate(@Qualifier("documentKafkaTemplate") KafkaTemplate<String, Document> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Autowired
    public DocumentCategorizer(ProcessingEventNotifier eventNotifier) {
        this.eventNotifier = eventNotifier;
    }

    @Override
    public Document process(Document document) {
        try {
            eventNotifier.notifyObservers(ProcessingEvent.create(
                document.getId(),
                "CATEGORIZED",
                "Starting categorization",
                85,
                "Categorizer"
            ));

            // Categorize based on content
            Document.DocumentCategory category = categorizeDocument(document);
            document.setCategory(category);
            document.setStatus(Document.DocumentStatus.CATEGORIZED);

            eventNotifier.notifyObservers(ProcessingEvent.create(
                document.getId(),
                "CATEGORIZED",
                "Document categorized as: " + category,
                90,
                "Categorizer"
            ));

            // Send to next stage via Kafka (if available)
            if (kafkaTemplate != null) {
                try {
                    String documentId = document.getId();
                    if (documentId != null) {
                        kafkaTemplate.send("document-categorized", documentId, document);
                    }
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
            document.setErrorMessage("Categorization failed: " + e.getMessage());

            eventNotifier.notifyObservers(ProcessingEvent.createError(
                document.getId(),
                "ERROR",
                "Categorization failed",
                "Categorizer",
                e.getMessage()
            ));

            return document;
        }
    }

    private Document.DocumentCategory categorizeDocument(Document document) {
        String content = document.getContentText();
        if (content == null || content.isEmpty()) {
            return Document.DocumentCategory.OTHER;
        }

        content = content.toLowerCase();

        // Count matches for each category
        int contractScore = countMatches(content, CONTRACT_PATTERN);
        int motionScore = countMatches(content, MOTION_PATTERN);
        int briefScore = countMatches(content, BRIEF_PATTERN);
        int evidenceScore = countMatches(content, EVIDENCE_PATTERN);
        int correspondenceScore = countMatches(content, CORRESPONDENCE_PATTERN);

        // Return category with highest score
        int maxScore = Math.max(Math.max(Math.max(Math.max(
            contractScore, motionScore), briefScore), evidenceScore), correspondenceScore);

        if (maxScore == 0) {
            return Document.DocumentCategory.OTHER;
        }

        if (contractScore == maxScore) return Document.DocumentCategory.CONTRACT;
        if (motionScore == maxScore) return Document.DocumentCategory.MOTION;
        if (briefScore == maxScore) return Document.DocumentCategory.BRIEF;
        if (evidenceScore == maxScore) return Document.DocumentCategory.EVIDENCE;
        if (correspondenceScore == maxScore) return Document.DocumentCategory.CORRESPONDENCE;

        return Document.DocumentCategory.OTHER;
    }

    private int countMatches(String content, Pattern pattern) {
        int count = 0;
        java.util.regex.Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    @KafkaListener(topics = "document-validated", groupId = "document-processing-group")
    public void consume(Document document) {
        process(document);
    }

    @Override
    public String getFilterName() {
        return "DocumentCategorizer";
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
