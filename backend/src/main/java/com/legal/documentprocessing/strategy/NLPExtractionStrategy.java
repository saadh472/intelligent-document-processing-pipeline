package com.legal.documentprocessing.strategy;

import com.legal.documentprocessing.model.ExtractedData;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Strategy Pattern: NLP-based extraction strategy
 * Uses natural language processing techniques for extraction
 */
@Component
public class NLPExtractionStrategy implements IExtractionStrategy {

    // Simple NLP patterns - in production, use libraries like Stanford NLP or spaCy
    private static final Pattern LEGAL_TERMS = Pattern.compile(
        "\\b(plaintiff|defendant|witness|testimony|evidence|judgment|verdict|appeal|court|judge|attorney)\\b",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PARTIES = Pattern.compile(
        "\\b([A-Z][a-z]+\\s+[A-Z][a-z]+)\\s+v\\.\\s+([A-Z][a-z]+\\s+[A-Z][a-z]+)\\b"
    );
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?]+");

    @Override
    public ExtractedData extract(String content) {
        Map<String, Object> extractedFields = new HashMap<>();
        Map<String, String> metadata = new HashMap<>();

        // Extract legal terms
        extractedFields.put("legalTerms", extractMatches(content, LEGAL_TERMS));

        // Extract parties (plaintiff vs defendant)
        extractedFields.put("parties", extractMatches(content, PARTIES));

        // Sentence count (simple NLP metric)
        String[] sentences = SENTENCE_END.split(content);
        extractedFields.put("sentenceCount", sentences.length);

        // Word count
        String[] words = content.split("\\s+");
        extractedFields.put("wordCount", words.length);

        // Extract key phrases (simple approach - sentences with legal terms)
        extractedFields.put("keyPhrases", extractKeyPhrases(content));

        metadata.put("extractionMethod", "NLP");
        metadata.put("language", "en");

        return ExtractedData.builder()
                .extractionStrategy("NLP")
                .extractedFields(extractedFields)
                .metadata(metadata)
                .fullText(content)
                .extractionTime(LocalDateTime.now())
                .confidenceScore(0.85)
                .build();
    }

    private String extractMatches(String content, Pattern pattern) {
        java.util.regex.Matcher matcher = pattern.matcher(content);
        StringBuilder matches = new StringBuilder();
        while (matcher.find()) {
            if (matches.length() > 0) matches.append(", ");
            matches.append(matcher.group());
        }
        return matches.toString();
    }

    private String extractKeyPhrases(String content) {
        // Simple key phrase extraction - sentences containing legal terms
        String[] sentences = content.split("[.!?]+");
        StringBuilder keyPhrases = new StringBuilder();

        for (String sentence : sentences) {
            if (LEGAL_TERMS.matcher(sentence).find() && sentence.trim().length() > 20) {
                if (keyPhrases.length() > 0) keyPhrases.append(" | ");
                keyPhrases.append(sentence.trim().substring(0, Math.min(100, sentence.trim().length())));
            }
        }
        return keyPhrases.toString();
    }

    @Override
    public String getStrategyType() {
        return "NLP";
    }

    @Override
    public boolean canHandle(String documentType) {
        return documentType != null &&
               (documentType.equalsIgnoreCase("pdf") ||
                documentType.equalsIgnoreCase("doc") ||
                documentType.equalsIgnoreCase("docx"));
    }
}
