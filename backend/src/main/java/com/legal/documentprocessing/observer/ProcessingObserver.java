package com.legal.documentprocessing.observer;

import com.legal.documentprocessing.model.ProcessingEvent;

/**
 * Observer Pattern: Interface for observers that track processing progress
 * Multiple dashboards can observe processing events
 */
public interface ProcessingObserver {
    /**
     * Called when a processing event occurs
     */
    void update(ProcessingEvent event);

    /**
     * Gets the observer ID
     */
    String getObserverId();
}
