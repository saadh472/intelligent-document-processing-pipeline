package com.legal.documentprocessing.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Factory for selecting appropriate extraction strategy
 * Based on document type at runtime
 */
@Component
public class ExtractionStrategyFactory {

    private final List<IExtractionStrategy> strategies;

    @Autowired
    public ExtractionStrategyFactory(List<IExtractionStrategy> strategies) {
        this.strategies = strategies;
    }

    /**
     * Selects the appropriate extraction strategy based on document type
     * @param documentType File extension or MIME type
     * @return Appropriate strategy, or default to NLP if none found
     */
    public IExtractionStrategy getStrategy(String documentType) {
        return strategies.stream()
                .filter(strategy -> strategy.canHandle(documentType))
                .findFirst()
                .orElse(strategies.stream()
                        .filter(s -> s.getStrategyType().equals("NLP"))
                        .findFirst()
                        .orElse(strategies.get(0))); // Fallback to first available
    }
}
