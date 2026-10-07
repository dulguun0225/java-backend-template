package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.startertest.BoomController;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The framework edge: every error is coded, no exception message reaches the wire, every response is correlated. A
 * path variable that does not parse is a {@code validation.failed} entry at its {@code in} and {@code name}; a
 * request body over the limit is the one 413, carrying the limit as {@code max}; a multipart request is 415.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, BoomController.class})
class ApiErrorEdgeIT {

    private static final String SENTINEL = "SENTINEL-path-3b9e";

    @LocalServerPort
    int port;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build();
    }

    @Test
    void unknownRouteIsACodedNotFound() {
        ResponseEntity<String> response =
                client().get().uri("/api/nowhere").retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("\"code\":\"not-found\"");
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isNotBlank();
    }

    @Test
    void malformedBodyIsACodedBadRequest() {
        ResponseEntity<String> response = client().post()
                .uri(URI.create("/api/greetings"))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{not json")
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("\"code\":\"validation.malformed-body\"");
    }

    @Test
    void wrongMethodIsCoded() {
        ResponseEntity<String> response =
                client().delete().uri("/api/greetings").retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).contains("\"code\":\"request.method-not-allowed\"");
    }

    /**
     * A path variable that does not parse is one {@code validation.failed} entry at the {@code in} and {@code name}
     * the committed document declares for it, {@code expected} its schema format, and never quotes the value sent,
     * which Spring's own {@code detail} does. RFC 9457's {@code instance} is the request URI, so it holds the path as
     * sent; nothing else does. A UUID is its 36-character form only: Spring's own conversion trims it and takes
     * {@code 1-1-1-1-1}.
     */
    @Test
    void aPathVariableThatDoesNotParseIsAnInvalidValueAtItsLocationAndName() throws IOException {
        JsonNode declared = JSON.readTree(Files.readString(OpenApiSnapshotIT.COMMITTED, StandardCharsets.UTF_8))
                .get("paths")
                .get("/api/greetings/{id}")
                .get("get")
                .get("parameters")
                .get(0);
        Map<String, Object> entry = Map.of(
                "in",
                declared.get("in").asString(),
                "name",
                declared.get("name").asString(),
                "code",
                "validation.invalid-value",
                "params",
                Map.of("expected", declared.get("schema").get("format").asString()),
                "detail",
                "expected uuid");
        assertThat(entry).containsEntry("in", "path").containsEntry("name", "id");
        for (String id : List.of(SENTINEL, "abc", "1-1-1-1-1", " 0190f0c4-7d2e-7b3a-9c4d-5e6f7a8b9c0d")) {
            ResponseEntity<String> response =
                    client().get().uri("/api/greetings/{id}", id).retrieve().toEntity(String.class);
            assertThat(response.getStatusCode()).as(id).isEqualTo(HttpStatus.BAD_REQUEST);
            Map<String, Object> problem = problem(response);
            assertThat(problem)
                    .as(id)
                    .containsEntry("status", 400)
                    .containsEntry("code", "validation.failed")
                    .containsEntry("errors", List.of(entry))
                    .doesNotContainKey("errorsOmitted");
            problem.remove("instance");
            assertThat(problem.toString())
                    .as("only `instance`, the request URI itself, carries the path sent")
                    .doesNotContain(SENTINEL);
        }
    }

    /**
     * A multipart request is 415, one over Spring's 1 MB multipart file limit included: the multipart resolver is
     * off, so nothing parses a multipart body and the strict reader's limit is the one source of a 413. With the
     * resolver on, this upload is refused by the resolver's own limit, a 413 that cannot carry {@code max}, which
     * the edge answers as the catch-all 500.
     */
    @Test
    void aMultipartRequestIsAnUnsupportedMediaType() {
        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("file", new byte[1536 * 1024]).filename("big.bin");
        ResponseEntity<String> response = client().post()
                .uri("/api/greetings")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts.build())
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(problem(response)).containsEntry("code", "request.unsupported-media-type");
    }

    /**
     * A body of exactly the limit, 65,536 bytes by default, is read; one byte more is 413 {@code request.too-large}
     * with the limit as {@code max}, both with a declared {@code Content-Length} and without one, when the body
     * arrives in chunks of unknown total length.
     */
    @Test
    void aBodyOfTheLimitIsReadAndOneByteMoreIsTooLargeNamingTheLimit() {
        int limit = 65_536;
        String head = "{\"name\":\"Ada\"";
        String exactly = head + " ".repeat(limit - head.length() - 1) + "}";
        assertThat(exactly.getBytes(StandardCharsets.UTF_8)).hasSize(limit);
        for (boolean declared : List.of(true, false)) {
            HttpResponse<String> read = post(exactly + "", declared);
            assertThat(read.statusCode())
                    .as("declared length " + declared + ": " + read.body())
                    .isEqualTo(201);

            HttpResponse<String> refused = post(exactly + " ", declared);
            assertThat(refused.statusCode()).as("declared length " + declared).isEqualTo(413);
            assertThat(JSON.readValue(refused.body(), Map.class))
                    .containsEntry("status", 413)
                    .containsEntry("code", "request.too-large")
                    .containsEntry("params", Map.of("max", limit))
                    .containsEntry("detail", "The request body must be at most 65536 bytes.");
        }
    }

    /** POSTs {@code json} to the greetings, with a {@code Content-Length} or, when not {@code declared}, chunked. */
    private HttpResponse<String> post(String json, boolean declared) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        HttpRequest.BodyPublisher body = declared
                ? HttpRequest.BodyPublishers.ofByteArray(bytes)
                : HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/greetings"))
                .header("Content-Type", "application/json")
                .POST(body)
                .build();
        try (HttpClient client =
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * A client-error status no catalog code carries is a missing catalog entry: the catch-all 500 with an
     * incident id, never another code's wire string under a status that code does not have.
     */
    @Test
    void aClientErrorStatusNoCodeCarriesIsTheCatchAll500() {
        ResponseEntity<String> response =
                client().get().uri(BoomController.UNCODED_PATH).retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(problem(response)).containsEntry("code", "platform.internal").containsKey("incidentId");
        assertThat(response.getBody()).doesNotContain(BoomController.SENTINEL);
    }

    /** A 413 raised by anything but the body limit could not say the {@code max} every 413 carries: the catch-all. */
    @Test
    void aTooLargeStatusFromAnythingButTheBodyLimitIsTheCatchAll500() {
        ResponseEntity<String> response =
                client().get().uri(BoomController.TOO_LARGE_PATH).retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(problem(response)).containsEntry("code", "platform.internal").containsKey("incidentId");
        assertThat(response.getBody()).doesNotContain(BoomController.SENTINEL);
    }

    /** Spring's own {@code detail} for 405 quotes the method sent; ours names the methods the route takes. */
    @Test
    void aMethodNotSupportedNamesTheSupportedOnesAndNotTheOneSent() {
        ResponseEntity<String> response = client().method(HttpMethod.valueOf("SENTINELMETHOD"))
                .uri("/api/greetings")
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(problem(response))
                .containsEntry("code", "request.method-not-allowed")
                .containsEntry("detail", "Supported methods: POST.");
        assertThat(response.getBody()).doesNotContain("SENTINELMETHOD");
    }

    /** Spring's own {@code detail} for 415 quotes the content type sent; ours names the ones the route takes. */
    @Test
    void anUnsupportedContentTypeNamesTheSupportedOnesAndNotTheOneSent() {
        ResponseEntity<String> response = client().post()
                .uri("/api/greetings")
                .contentType(MediaType.parseMediaType("text/sentinel-type"))
                .body("x")
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(problem(response))
                .containsEntry("code", "request.unsupported-media-type")
                .hasEntrySatisfying(
                        "detail", detail -> assertThat(String.valueOf(detail)).startsWith("Supported content types: "));
        assertThat(response.getBody()).doesNotContain("sentinel-type");
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> problem(ResponseEntity<String> response) {
        return JSON.readValue(Objects.requireNonNull(response.getBody()), Map.class);
    }
}
