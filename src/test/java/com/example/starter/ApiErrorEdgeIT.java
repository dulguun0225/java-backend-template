package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.startertest.BoomController;
import java.net.URI;
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
import tools.jackson.databind.json.JsonMapper;

/** The framework edge: every error is coded, no exception message reaches the wire, every response is correlated. */
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
     * A path variable that does not convert names the variable and its type in {@code detail}, and never quotes
     * the value sent, which Spring's own {@code detail} does. RFC 9457's {@code instance} is the request URI, so it
     * holds the path as sent; nothing else does.
     */
    @Test
    void aPathVariableOfTheWrongTypeIsABadRequestNamingItWithoutEchoingTheValue() {
        ResponseEntity<String> response =
                client().get().uri("/api/greetings/{id}", SENTINEL).retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> problem = problem(response);
        assertThat(problem)
                .containsEntry("code", "validation.bad-request")
                .containsEntry("detail", "The path variable 'id' must be of type UUID.");
        problem.remove("instance");
        assertThat(problem.toString())
                .as("only `instance`, the request URI itself, carries the path sent")
                .doesNotContain(SENTINEL);
    }

    /** A multipart upload over the size limit is 413 under the code whose catalog status is 413. */
    @Test
    void anUploadOverTheLimitIsCodedTooLarge() {
        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("file", new byte[2 * 1024 * 1024]).filename("big.bin");
        ResponseEntity<String> response = client().post()
                .uri("/api/greetings")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts.build())
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(problem(response)).containsEntry("code", "request.too-large");
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> problem(ResponseEntity<String> response) {
        return JsonMapper.builder().build().readValue(Objects.requireNonNull(response.getBody()), Map.class);
    }
}
