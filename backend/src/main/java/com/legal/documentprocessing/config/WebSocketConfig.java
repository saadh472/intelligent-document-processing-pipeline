package com.legal.documentprocessing.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final String[] origins;
    private final WorkspaceSocketSessions sessions;

    public WebSocketConfig(@Value("${idp.allowed-origins}") String origins, WorkspaceSocketSessions sessions) {
        this.origins = Arrays.stream(origins.split(",")).map(String::strip).toArray(String[]::new);
        this.sessions = sessions;
    }

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(new HttpSessionHandshakeInterceptor())
                .setAllowedOrigins(origins)
                .withSockJS();
    }

    @Override
    public void configureWebSocketTransport(@NonNull WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(sessions::decorate);
        registration.setMessageSizeLimit(8192);
    }

    @Override
    public void configureClientInboundChannel(@NonNull ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
                StompCommand command = headers.getCommand();
                if (command == StompCommand.CONNECT || command == StompCommand.SUBSCRIBE) {
                    if (headers.getUser() == null) {
                        throw new AccessDeniedException("An authenticated workspace session is required.");
                    }
                }
                if (command == StompCommand.SEND) {
                    throw new AccessDeniedException("This WebSocket is read-only.");
                }
                if (command == StompCommand.SUBSCRIBE) {
                    String destination = headers.getDestination();
                    if (destination == null || !destination.matches("/topic/processing(?:/[a-zA-Z0-9-]+)?")) {
                        throw new AccessDeniedException("Unsupported subscription.");
                    }
                }
                return message;
            }
        });
    }
}
