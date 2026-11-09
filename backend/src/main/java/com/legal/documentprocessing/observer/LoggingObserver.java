package com.legal.documentprocessing.observer;

import com.legal.documentprocessing.model.ProcessingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Observer Pattern: Logging observer
 * Logs all processing events to console/file
 */
@Component
public class LoggingObserver implements ProcessingObserver {

    private static final Logger logger = LoggerFactory.getLogger(LoggingObserver.class);
    private final String observerId;

    public LoggingObserver() {
        this.observerId = "LoggingObserver";
    }

    @Override
    public void update(ProcessingEvent event) {
        String logMessage = String.format(
            "[%s] Document: %s | Stage: %s | Progress: %d%% | %s",
            event.getEventType(),
            event.getDocumentId(),
            event.getStage() != null ? event.getStage() : "N/A",
            event.getProgressPercentage(),
            event.getMessage()
        );

        switch (event.getEventType()) {
            case "ERROR":
                logger.error(logMessage);
                if (event.getErrorMessage() != null) {
                    logger.error("Error details: " + event.getErrorMessage());
                }
                break;
            case "COMPLETED":
                logger.info(logMessage);
                break;
            default:
                logger.debug(logMessage);
                break;
        }
    }

    @Override
    public String getObserverId() {
        return observerId;
    }
}
