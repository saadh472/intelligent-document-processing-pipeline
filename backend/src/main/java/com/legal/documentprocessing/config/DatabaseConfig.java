package com.legal.documentprocessing.config;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** File H2 uses a persistent random credential instead of an empty default. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DataSourceProperties.class)
public class DatabaseConfig {
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(DataSourceProperties properties, Environment environment,
            @Value("${idp.data-dir}") String dataDir) throws IOException {
        String url = properties.determineUrl();
        boolean h2 = url.startsWith("jdbc:h2:");
        String password = environment.getProperty("IDP_DATABASE_PASSWORD");
        if (password == null) password = properties.getPassword();
        boolean legacy = environment.getProperty("IDP_ALLOW_LEGACY_H2_PASSWORDLESS", Boolean.class, false);
        if (password == null) {
            if (!h2) {
                throw new IllegalStateException("Set IDP_DATABASE_PASSWORD for an external database.");
            }
            password = legacy ? "" : generatedPassword(Path.of(dataDir));
        }
        if (password.isBlank() && !(h2 && legacy)) {
            throw new IllegalStateException("Database passwords cannot be blank. Existing passwordless H2 installations require explicit temporary IDP_ALLOW_LEGACY_H2_PASSWORDLESS=true.");
        }
        properties.setPassword(password);
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    static String generatedPassword(Path directory) throws IOException {
        Path folder = directory.toAbsolutePath().normalize();
        Files.createDirectories(folder);
        Path secret = folder.resolve("database-password.txt");
        if (Files.isSymbolicLink(secret)) throw new IOException("The database credential file must not be a symbolic link.");
        if (!Files.exists(secret)) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            Files.writeString(secret, Base64.getUrlEncoder().withoutPadding().encodeToString(random) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.setPosixFilePermissions(secret, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows uses the data directory's inherited local ACL.
            }
        }
        String password = Files.readString(secret, StandardCharsets.UTF_8).strip();
        if (password.length() < 32) throw new IOException("The generated database credential file is invalid. Restore its private backup or configure IDP_DATABASE_PASSWORD.");
        return password;
    }
}
