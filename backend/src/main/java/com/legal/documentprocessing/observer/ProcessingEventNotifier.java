package com.legal.documentprocessing.observer;

import com.legal.documentprocessing.model.ProcessingEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Observer Pattern: Concrete subject implementation
 * Manages observers and notifies them of processing events
 */
@Component
public class ProcessingEventNotifier implements ProcessingSubject {

    private final List<ProcessingObserver> observers;

    public ProcessingEventNotifier() {
        // Use thread-safe list for concurrent access
        this.observers = new CopyOnWriteArrayList<>();
    }

    @Override
    public void attach(ProcessingObserver observer) {
        observers.add(observer);
    }

    @Override
    public void detach(ProcessingObserver observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyObservers(ProcessingEvent event) {
        for (ProcessingObserver observer : observers) {
            try {
                observer.update(event);
            } catch (Exception e) {
                // Log error but continue notifying other observers
                System.err.println("Error notifying observer " + observer.getObserverId() + ": " + e.getMessage());
            }
        }
    }

    public int getObserverCount() {
        return observers.size();
    }
}
