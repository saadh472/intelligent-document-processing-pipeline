package com.legal.documentprocessing.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseConfigTest {
    @TempDir Path data;
    private DataSourceProperties properties(String url) {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl(url); properties.setUsername("sa");
        return properties;
    }
    @Test void generatedCredentialIsPrivateStableAndNonempty() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        String first;
        try (var source = config.dataSource(properties("jdbc:h2:mem:credential-policy"), new MockEnvironment(), data.toString())) {
            first = source.getPassword();
            assertTrue(first.length() >= 32);
        }
        try (var source = config.dataSource(properties("jdbc:h2:mem:credential-policy"), new MockEnvironment(), data.toString())) {
            assertEquals(first, source.getPassword());
            assertEquals(first, Files.readString(data.resolve("database-password.txt")).strip());
        }
    }
    @Test void configuredCredentialDoesNotCreateAnotherSecretFile() throws Exception {
        MockEnvironment environment = new MockEnvironment().withProperty("IDP_DATABASE_PASSWORD", "ExternallyManagedTestSecret123!");
        try (var source = new DatabaseConfig().dataSource(properties("jdbc:h2:mem:configured-policy"), environment, data.toString())) {
            assertEquals("ExternallyManagedTestSecret123!", source.getPassword());
            assertFalse(Files.exists(data.resolve("database-password.txt")));
        }
    }
    @Test void passwordlessLegacyAccessRequiresAnExplicitLocalH2OptIn() throws Exception {
        MockEnvironment emptyPassword = new MockEnvironment().withProperty("IDP_DATABASE_PASSWORD", "");
        assertThrows(IllegalStateException.class, () -> new DatabaseConfig().dataSource(
                properties("jdbc:h2:mem:legacy-policy"), emptyPassword, data.toString()));
        MockEnvironment legacy = emptyPassword.withProperty("IDP_ALLOW_LEGACY_H2_PASSWORDLESS", "true");
        try (var source = new DatabaseConfig().dataSource(properties("jdbc:h2:mem:legacy-policy"), legacy, data.toString())) {
            assertEquals("", source.getPassword());
        }
        assertThrows(IllegalStateException.class, () -> new DatabaseConfig().dataSource(
                properties("jdbc:postgresql://localhost/test"), legacy, data.toString()),
                "The compatibility switch must never permit empty external database credentials");
    }
}
