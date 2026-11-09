package com.legal.documentprocessing.strategy;

import com.legal.documentprocessing.model.ExtractedData;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Strategy Pattern: OCR-based extraction strategy
 * Uses Apache Tika for OCR and document parsing
 */
@Component
public class OCRExtractionStrategy implements IExtractionStrategy {

    private final Tika tika = new Tika();

    @Override
    public ExtractedData extract(String content) {
        Map<String, Object> extractedFields = new HashMap<>();
        Map<String, String> metadata = new HashMap<>();

        try {
            // Use Tika to extract text and metadata from binary content
            byte[] contentBytes = content.getBytes();
            String extractedText = tika.parseToString(new ByteArrayInputStream(contentBytes));

            // Extract metadata
            org.apache.tika.metadata.Metadata tikaMetadata = new org.apache.tika.metadata.Metadata();
            tika.parse(new ByteArrayInputStream(contentBytes), tikaMetadata);

            // Convert Tika metadata to our format
            for (String name : tikaMetadata.names()) {
                metadata.put(name, tikaMetadata.get(name));
            }

            extractedFields.put("ocrText", extractedText);
            extractedFields.put("characterCount", extractedText.length());
            extractedFields.put("hasText", !extractedText.trim().isEmpty());

            metadata.put("extractionMethod", "OCR");
            metadata.put("tikaVersion", tika.getClass().getPackage().getImplementationVersion());

            return ExtractedData.builder()
                    .extractionStrategy("OCR")
                    .extractedFields(extractedFields)
                    .metadata(metadata)
                    .fullText(extractedText)
                    .extractionTime(LocalDateTime.now())
                    .confidenceScore(0.80)
                    .build();

        } catch (IOException | TikaException e) {
            // Fallback to basic extraction
            extractedFields.put("error", "OCR processing failed: " + e.getMessage());
            extractedFields.put("rawContent", content);

            return ExtractedData.builder()
                    .extractionStrategy("OCR")
                    .extractedFields(extractedFields)
                    .metadata(metadata)
                    .fullText(content)
                    .extractionTime(LocalDateTime.now())
                    .confidenceScore(0.30)
                    .build();
        }
    }

    @Override
    public String getStrategyType() {
        return "OCR";
    }

    @Override
    public boolean canHandle(String documentType) {
        return documentType != null &&
               (documentType.equalsIgnoreCase("jpg") ||
                documentType.equalsIgnoreCase("jpeg") ||
                documentType.equalsIgnoreCase("png") ||
                documentType.equalsIgnoreCase("tiff") ||
                documentType.equalsIgnoreCase("pdf")); // PDFs may contain images
    }
}
