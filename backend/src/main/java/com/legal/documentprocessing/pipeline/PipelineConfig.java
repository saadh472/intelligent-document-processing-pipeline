package com.legal.documentprocessing.pipeline;

import com.legal.documentprocessing.observer.LoggingObserver;
import com.legal.documentprocessing.observer.ProcessingEventNotifier;
import com.legal.documentprocessing.observer.WebSocketObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Configuration for Pipes and Filters pipeline
 * Sets up the pipeline chain and observers
 */
@Configuration
public class PipelineConfig {

    @Bean
    public ProcessingEventNotifier processingEventNotifier(
            @Autowired(required = false) SimpMessagingTemplate messagingTemplate,
            LoggingObserver loggingObserver) {
        ProcessingEventNotifier notifier = new ProcessingEventNotifier();

        // Attach logging observer
        notifier.attach(loggingObserver);

        // Attach WebSocket observer if available
        if (messagingTemplate != null) {
            WebSocketObserver webSocketObserver = new WebSocketObserver(messagingTemplate);
            notifier.attach(webSocketObserver);
        }

        return notifier;
    }
}
