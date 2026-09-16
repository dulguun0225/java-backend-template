package com.example.starter.greeting;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.starter.TestcontainersConfiguration;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** The worked-example slice end to end: HTTP, validation, tx.*, generated jOOQ, real PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class GreetingEndpointIT {

    @LocalServerPort
    int port;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build();
    }

    @Test
    void createThenRead() {
        ResponseEntity<GreetingView> created = client().post()
                .uri("/api/greetings")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"Ada\"}")
                .retrieve()
                .toEntity(GreetingView.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        GreetingView view = Objects.requireNonNull(created.getBody());
        assertThat(view.id().version()).as("ids are UUIDv7").isEqualTo(7);
        assertThat(view.message()).isEqualTo("Hello, Ada!");
        assertThat(created.getHeaders().getLocation()).hasPath("/api/greetings/" + view.id());

        ResponseEntity<GreetingView> read =
                client().get().uri("/api/greetings/{id}", view.id()).retrieve().toEntity(GreetingView.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read.getBody()).isEqualTo(view);
    }

    @Test
    void aBlankNameIsAFieldLevelValidationFailure() {
        ResponseEntity<String> response = client().post()
                .uri("/api/greetings")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"  \"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .contains("\"code\":\"validation.failed\"")
                .contains("\"pointer\":\"/name\"")
                .contains("\"code\":\"validation.required\"");
    }

    @Test
    void anUnknownIdIsACodedNotFound() {
        ResponseEntity<String> response = client().get()
                .uri("/api/greetings/{id}", UUID.fromString("00000000-0000-7000-8000-000000000000"))
                .retrieve()
                .toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("\"code\":\"not-found\"");
    }
}
