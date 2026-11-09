package com.legal.documentprocessing.strategy;

import com.legal.documentprocessing.model.ExtractedData;

/**
 * Strategy Pattern: Interface for extraction algorithms
 * Multiple implementations: OCR, Regex, NLP
 */
public interface IExtractionStrategy {
    /**
     * Extracts data from document content
     * @param content Raw document content
     * @return ExtractedData containing structured information
     */
    ExtractedData extract(String content);

    /**
     * Returns the strategy name/type
     */
    String getStrategyType();

    /**
     * Checks if this strategy can handle the given document type
     */
    boolean canHandle(String documentType);
}
