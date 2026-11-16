package com.legal.documentprocessing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.messaging.simp.user.SimpUserRegistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Real HTTP + real file database, closed and reopened between application instances. */
class WorkspaceSecurityPersistenceTest {
    @TempDir
    Path data;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void sessionsCsrfAndDocumentsSurviveOnlyTheIntendedBoundaries() throws Exception {
        String id;
        String databasePassword;
        try (ConfigurableApplicationContext first = start()) {
            Browser browser = browser(first);
            assertEquals(401, browser.get("/api/documents").statusCode());
            assertEquals(401, browser.get("/ws/info").statusCode());
            assertEquals(200, browser.get("/api/health").statusCode());

            JsonNode anonymous = json.readTree(browser.get("/api/auth/session").body());
            assertFalse(anonymous.get("authenticated").asBoolean());
            String csrf = anonymous.get("csrfToken").asText();
            assertFalse(csrf.isBlank());
            assertEquals(403, browser.post("/api/auth/login", null,
                    "{\"username\":\"admin\",\"password\":\"TestWorkspacePassword!123\"}").statusCode());
            assertEquals(401, browser.post("/api/auth/login", csrf,
                    "{\"username\":\"admin\",\"password\":\"incorrect\"}").statusCode());

            String originalSession = browser.sessionId();
            HttpResponse<String> login = browser.login(csrf);
            assertEquals(200, login.statusCode());
            JsonNode signedIn = json.readTree(login.body());
            assertTrue(signedIn.get("authenticated").asBoolean());
            assertEquals("admin", signedIn.get("username").asText());
            String freshCsrf = signedIn.get("csrfToken").asText();
            assertNotEquals(csrf, freshCsrf, "Login rotates the CSRF token");
            assertNotEquals(originalSession, browser.sessionId(), "Login prevents session fixation");
            assertTrue(json.readTree(browser.get("/api/auth/session").body()).get("authenticated").asBoolean());
            assertEquals(403, browser.upload(null).statusCode());
            assertEquals(403, browser.upload(csrf).statusCode(), "Pre-login CSRF token is invalidated");

            SocketListener live = new SocketListener();
            WebSocket socket = browser.connectSocket(live);
            socket.sendText("CONNECT\naccept-version:1.2\nheart-beat:0,0\n\n\0", true).get(5, TimeUnit.SECONDS);
            assertTrue(live.frames.poll(5, TimeUnit.SECONDS).startsWith("CONNECTED"));
            socket.sendText("SUBSCRIBE\nid:updates\ndestination:/topic/processing\n\n\0", true)
                    .get(5, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(5)).until(() -> {
                var user = first.getBean(SimpUserRegistry.class).getUser("admin");
                return user != null && user.getSessions().stream().anyMatch(session ->
                        session.getSubscriptions().stream().anyMatch(subscription ->
                                subscription.getDestination().equals("/topic/processing")));
            });

            HttpResponse<String> upload = browser.upload(freshCsrf);
            assertEquals(201, upload.statusCode(), upload.body());
            id = json.readTree(upload.body()).get("id").asText();
            assertTrue(live.frames.poll(5, TimeUnit.SECONDS).contains(id), "Authenticated subscriber receives upload events");
            HttpResponse<String> process = browser.post("/api/documents/" + id + "/process", freshCsrf, "{}");
            assertEquals(200, process.statusCode(), process.body());
            assertEquals("COMPLETED", json.readTree(process.body()).get("status").asText());
            assertEquals("CONTRACT", json.readTree(process.body()).get("category").asText());
            assertTrue(json.readTree(process.body()).get("contentText").asText().contains("Fictional Supply Agreement"));
            assertTrue(browser.get("/api/documents").body().contains(id));

            assertEquals(403, browser.post("/api/auth/logout", null, "{}").statusCode());
            HttpResponse<String> logout = browser.post("/api/auth/logout", freshCsrf, "{}");
            assertEquals(200, logout.statusCode());
            assertFalse(json.readTree(logout.body()).get("authenticated").asBoolean());
            assertEquals(1008, live.closed.get(5, TimeUnit.SECONDS), "Logout closes the live data subscription");
            assertNotEquals(freshCsrf, json.readTree(logout.body()).get("csrfToken").asText());
            assertEquals(401, browser.get("/api/documents/" + id).statusCode());
            databasePassword = Files.readString(data.resolve("database-password.txt")).strip();
            assertTrue(databasePassword.length() >= 32, "The local database has a generated nonempty secret");
        }

        // No shared Spring test context or in-memory DB: this starts a new server against the same files.
        String databaseUrl = "jdbc:h2:file:" + data.resolve("documents").toString().replace('\\', '/') + ";IFEXISTS=TRUE";
        assertThrows(SQLException.class, () -> DriverManager.getConnection(databaseUrl, "sa", ""),
                "A fresh local database cannot be opened with an empty password");
        try (ConfigurableApplicationContext second = start()) {
            assertEquals(databasePassword, Files.readString(data.resolve("database-password.txt")).strip(),
                    "Restart reuses the same database credential");
            Browser browser = browser(second);
            assertEquals(401, browser.get("/api/documents/" + id).statusCode());
            String csrf = json.readTree(browser.get("/api/auth/session").body()).get("csrfToken").asText();
            assertEquals(200, browser.login(csrf).statusCode());
            HttpResponse<String> restored = browser.get("/api/documents/" + id);
            assertEquals(200, restored.statusCode(), restored.body());
            JsonNode document = json.readTree(restored.body());
            assertEquals("COMPLETED", document.get("status").asText());
            assertEquals("CONTRACT", document.get("category").asText());
            assertTrue(document.get("contentText").asText().contains("Fictional Supply Agreement"));
            assertTrue(browser.get("/api/documents/status/COMPLETED").body().contains(id));
            assertTrue(browser.get("/api/documents/category/CONTRACT").body().contains(id));
        }
    }

    private ConfigurableApplicationContext start() {
        String location = data.resolve("documents").toString().replace('\\', '/');
        return new SpringApplicationBuilder(DocumentProcessingApplication.class).run(
                "--server.port=0",
                "--server.address=127.0.0.1",
                "--spring.datasource.url=jdbc:h2:file:" + location + ";DB_CLOSE_ON_EXIT=FALSE",
                "--idp.data-dir=" + data.toString(),
                "--idp.auth.username=admin",
                "--idp.auth.password=TestWorkspacePassword!123");
    }

    private Browser browser(ConfigurableApplicationContext context) {
        int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
        return new Browser("http://127.0.0.1:" + port);
    }

    private static class Browser {
        private final String base;
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10)).build();

        Browser(String base) {
            this.base = base;
        }

        String sessionId() {
            return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("JSESSIONID"))
                    .map(c -> c.getValue()).findFirst().orElse("");
        }

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, String csrf, String body) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
            if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> login(String csrf) throws Exception {
            return post("/api/auth/login", csrf,
                    "{\"username\":\"admin\",\"password\":\"TestWorkspacePassword!123\"}");
        }

        WebSocket connectSocket(WebSocket.Listener listener) throws Exception {
            return client.newWebSocketBuilder().header("Origin", "http://127.0.0.1:3000")
                    .buildAsync(URI.create(base.replace("http://", "ws://") + "/ws/websocket"), listener)
                    .get(10, TimeUnit.SECONDS);
        }

        HttpResponse<String> upload(String csrf) throws Exception {
            String boundary = "workspace-integration-boundary";
            String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"fictional-agreement.txt\""
                    + "\r\nContent-Type: text/plain\r\n\r\nFictional Supply Agreement\n"
                    + "On 04/15/2025, the parties signed this contract for sample supplies.\n"
                    + "The agreement sets the terms and conditions for the demonstration order.\r\n--" + boundary + "--\r\n";
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + "/api/documents/upload"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static class SocketListener implements WebSocket.Listener {
        final LinkedBlockingQueue<String> frames = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        private final StringBuilder incoming = new StringBuilder();

        @Override
        public void onOpen(WebSocket socket) {
            socket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            incoming.append(data);
            if (last) {
                frames.add(incoming.toString());
                incoming.setLength(0);
            }
            socket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
            closed.complete(code);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            closed.completeExceptionally(error);
        }
    }
}
