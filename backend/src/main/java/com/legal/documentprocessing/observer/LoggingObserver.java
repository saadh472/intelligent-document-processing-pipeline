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
        switch (event.getEventType()) {
            case "ERROR":
                logger.error("Document processing reported an error");
                break;
            case "COMPLETED":
                logger.info("Document processing completed");
                break;
            default:
                logger.debug("Document processing progress: {}%", Math.max(0, Math.min(100, event.getProgressPercentage())));
                break;
        }
    }

    @Override
    public String getObserverId() {
        return observerId;
    }
}
