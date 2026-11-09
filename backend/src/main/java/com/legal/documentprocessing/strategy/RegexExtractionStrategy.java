package com.legal.documentprocessing.strategy;

import com.legal.documentprocessing.model.ExtractedData;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy Pattern: Regex-based extraction strategy
 * Extracts structured data using regular expressions
 */
@Component
public class RegexExtractionStrategy implements IExtractionStrategy {

    private static final Pattern DATE_PATTERN = Pattern.compile(
        "\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\b"
    );
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
    );
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "\\b(\\+?\\d{1,3}[-.]?)?\\(?\\d{3}\\)?[-.]?\\d{3}[-.]?\\d{4}\\b"
    );
    private static final Pattern CASE_NUMBER_PATTERN = Pattern.compile(
        "\\b(Case|CASE|case)[\\s#:]+([A-Z0-9-]+)\\b"
    );

    @Override
    public ExtractedData extract(String content) {
        Map<String, Object> extractedFields = new HashMap<>();
        Map<String, String> metadata = new HashMap<>();

        // Extract dates
        extractedFields.put("dates", extractMatches(content, DATE_PATTERN));

        // Extract emails
        extractedFields.put("emails", extractMatches(content, EMAIL_PATTERN));

        // Extract phone numbers
        extractedFields.put("phones", extractMatches(content, PHONE_PATTERN));

        // Extract case numbers
        extractedFields.put("caseNumbers", extractMatches(content, CASE_NUMBER_PATTERN));

        metadata.put("extractionMethod", "Regex");
        metadata.put("patternCount", String.valueOf(extractedFields.size()));

        return ExtractedData.builder()
                .extractionStrategy("REGEX")
                .extractedFields(extractedFields)
                .metadata(metadata)
                .fullText(content)
                .extractionTime(LocalDateTime.now())
                .confidenceScore(0.75)
                .build();
    }

    private String extractMatches(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        StringBuilder matches = new StringBuilder();
        while (matcher.find()) {
            if (matches.length() > 0) matches.append(", ");
            matches.append(matcher.group());
        }
        return matches.toString();
    }

    @Override
    public String getStrategyType() {
        return "REGEX";
    }

    @Override
    public boolean canHandle(String documentType) {
        return documentType != null &&
               (documentType.equalsIgnoreCase("txt") ||
                documentType.equalsIgnoreCase("text"));
    }
}
