package com.legal.documentprocessing.config;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/** Closes live subscriptions when the owning servlet session ends. */
@Configuration
public class WorkspaceSocketSessions implements HttpSessionListener {
    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                String httpId = httpId(session);
                if (httpId == null || session.getPrincipal() == null) {
                    session.close(CloseStatus.POLICY_VIOLATION);
                    return;
                }
                sessions.computeIfAbsent(httpId, key -> ConcurrentHashMap.newKeySet()).add(session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                String httpId = httpId(session);
                if (httpId != null) {
                    sessions.computeIfPresent(httpId, (key, entries) -> {
                        entries.remove(session);
                        return entries.isEmpty() ? null : entries;
                    });
                }
                super.afterConnectionClosed(session, status);
            }
        };
    }

    public void closeForSession(String httpSessionId) {
        Set<WebSocketSession> entries = sessions.remove(httpSessionId);
        if (entries != null) {
            entries.forEach(session -> {
                try {
                    session.close(CloseStatus.POLICY_VIOLATION);
                } catch (IOException ignored) {
                    // A concurrently closed connection is already no longer subscribed.
                }
            });
        }
    }

    private String httpId(WebSocketSession session) {
        Object value = session.getAttributes().get(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME);
        return value instanceof String ? (String) value : null;
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        closeForSession(event.getSession().getId());
    }

    @Bean
    ServletListenerRegistrationBean<HttpSessionListener> workspaceSessionListener() {
        return new ServletListenerRegistrationBean<>(this);
    }
}
