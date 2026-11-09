package com.legal.documentprocessing.observer;

import com.legal.documentprocessing.model.ProcessingEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Observer Pattern: WebSocket-based observer
 * Sends real-time updates to connected clients via WebSocket
 */
@Component
public class WebSocketObserver implements ProcessingObserver {

    private final SimpMessagingTemplate messagingTemplate;
    private final String observerId;

    public WebSocketObserver(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.observerId = "WebSocketObserver-" + System.currentTimeMillis();
    }

    @Override
    public void update(ProcessingEvent event) {
        // Send to topic: /topic/processing/{documentId}
        String destination = "/topic/processing/" + event.getDocumentId();
        messagingTemplate.convertAndSend(destination, event);

        // Also send to general processing topic
        messagingTemplate.convertAndSend("/topic/processing", event);
    }

    @Override
    public String getObserverId() {
        return observerId;
    }
}
