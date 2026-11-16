package com.legal.documentprocessing.controller;

import com.legal.documentprocessing.config.WorkspaceSocketSessions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final HttpSessionSecurityContextRepository contexts;
    private final CsrfTokenRepository tokens;
    private final WorkspaceSocketSessions sockets;

    public AuthController(AuthenticationManager authenticationManager, HttpSessionSecurityContextRepository contexts,
                          CsrfTokenRepository tokens, WorkspaceSocketSessions sockets) {
        this.authenticationManager = authenticationManager;
        this.contexts = contexts;
        this.tokens = tokens;
        this.sockets = sockets;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/auth/session")
    public Map<String, Object> session(HttpServletRequest request, HttpServletResponse response,
                                       Authentication authentication) {
        response.setHeader("Cache-Control", "no-store");
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return payload(authentication, token.getToken());
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@Valid @RequestBody Credentials credentials,
                                   HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        try {
            Authentication auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(credentials.username(), credentials.password()));
            HttpSession previous = request.getSession(false);
            if (previous != null) {
                sockets.closeForSession(previous.getId());
            }
            new ChangeSessionIdAuthenticationStrategy().onAuthentication(auth, request, response);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context, request, response);
            return ResponseEntity.ok(payload(auth, rotateToken(request, response)));
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "The username or password is incorrect."));
        }
    }

    @PostMapping("/auth/logout")
    public Map<String, Object> logout(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        HttpSession session = request.getSession(false);
        if (session != null) {
            sockets.closeForSession(session.getId());
            session.invalidate();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return payload(null, rotateToken(request, response));
    }

    private String rotateToken(HttpServletRequest request, HttpServletResponse response) {
        tokens.saveToken(null, request, response);
        CsrfToken token = tokens.generateToken(request);
        tokens.saveToken(token, request, response);
        return token.getToken();
    }

    private Map<String, Object> payload(Authentication authentication, String csrfToken) {
        boolean authenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("authenticated", authenticated);
        if (authenticated) {
            payload.put("username", authentication.getName());
        }
        payload.put("csrfToken", csrfToken);
        return payload;
    }

    public record Credentials(@NotBlank @Size(max = 128) String username,
                              @NotBlank @Size(max = 256) String password) {}
}
