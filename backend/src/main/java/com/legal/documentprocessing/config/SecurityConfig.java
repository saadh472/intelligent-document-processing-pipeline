package com.legal.documentprocessing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** One private workspace account; no shared public demo password is shipped. */
@Configuration
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService workspaceUser(@Value("${idp.auth.username}") String username,
                                    @Value("${idp.auth.password:}") String configuredPassword,
                                    @Value("${idp.data-dir}") String dataDir,
                                    PasswordEncoder encoder, Environment environment) throws IOException {
        String password = environment.getProperty("IDP_PASSWORD", configuredPassword);
        if (password.isBlank()) {
            Path folder = Path.of(dataDir).toAbsolutePath().normalize();
            Files.createDirectories(folder);
            Path secret = folder.resolve("bootstrap-password.txt");
            if (!Files.exists(secret)) {
                byte[] bytes = new byte[24];
                new SecureRandom().nextBytes(bytes);
                Files.writeString(secret, Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                try {
                    Files.setPosixFilePermissions(secret, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
                } catch (UnsupportedOperationException ignored) {
                    // Windows inherits the local data folder ACL.
                }
            }
            password = Files.readString(secret, StandardCharsets.UTF_8).strip();
            log.warn("Workspace password loaded from the private bootstrap file. Set IDP_PASSWORD to manage it externally.");
        }
        if (username.isBlank() || password.length() < 12) {
            throw new IllegalStateException("Configure a nonempty IDP_USERNAME and an IDP_PASSWORD of at least 12 characters.");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(encoder.encode(password)).roles("WORKSPACE").build());
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    HttpSessionSecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${idp.allowed-origins}") String origins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, HttpSessionSecurityContextRepository contexts,
                                           CsrfTokenRepository csrf, ObjectMapper mapper) throws Exception {
        http.cors(cors -> {})
                .csrf(config -> config.csrfTokenRepository(csrf)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // SockJS transport POSTs cannot send a CSRF header. HTTP authentication plus
                        // the explicit origin allowlist protect the handshake; client SEND is denied.
                        .ignoringRequestMatchers("/ws/**"))
                .securityContext(config -> config.securityContextRepository(contexts))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/auth/session", "/api/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                jsonError(response, mapper, 401, "Sign in to access this workspace."))
                        .accessDeniedHandler((request, response, exception) ->
                                jsonError(response, mapper, 403, "Request denied. Refresh your session and try again.")))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());
        return http.build();
    }

    private static void jsonError(HttpServletResponse response, ObjectMapper mapper, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), Map.of("message", message));
    }
}
