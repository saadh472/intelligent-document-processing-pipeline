package com.legal.documentprocessing.observer;

import com.legal.documentprocessing.model.ProcessingEvent;

/**
 * Observer Pattern: Subject interface for notifying observers
 */
public interface ProcessingSubject {
    /**
     * Attach an observer
     */
    void attach(ProcessingObserver observer);

    /**
     * Detach an observer
     */
    void detach(ProcessingObserver observer);

    /**
     * Notify all observers of an event
     */
    void notifyObservers(ProcessingEvent event);
}
