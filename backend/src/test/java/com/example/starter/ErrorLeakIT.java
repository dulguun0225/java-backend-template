package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import com.example.startertest.BoomController;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Two contracts of the error edge, pinned. An unexpected throwable becomes a coded 500 whose body never carries
 * the exception message, class name or stack (the sentinel below must be absent from the wire). And the
 * {@code incidentId} on that body is the request's correlation id, which resolves to exactly the log event the
 * facade emitted for it, so one id joins the client's 500 to the server's log line.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, BoomController.class})
class ErrorLeakIT {

    @LocalServerPort
    int port;

    private final CapturingAppender appender = new CapturingAppender();

    @BeforeEach
    void capture() {
        appender.start();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(appender);
    }

    @AfterEach
    void release() {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(appender);
    }

    @Test
    void anUnexpectedThrowableIsACodedFiveHundredWhoseIncidentIdResolvesToOneLogEvent() {
        ResponseEntity<String> response = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, resp) -> {})
                .build()
                .get()
                .uri(BoomController.PATH)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        String body = response.getBody();
        assertThat(body).isNotNull().contains("\"code\":\"platform.internal\"");
        assertThat(body).doesNotContain(BoomController.SENTINEL).doesNotContain("IllegalStateException");

        String correlationId = Objects.requireNonNull(response.getHeaders().getFirst("X-Correlation-Id"));
        assertThat(correlationId).isNotBlank();
        assertThat(body).contains("\"incidentId\":\"" + correlationId + "\"");

        assertThat(appender.events)
                .filteredOn(event -> "request.unhandled-error".equals(event.getMessage()))
                .filteredOn(
                        event -> correlationId.equals(event.getMDCPropertyMap().get("correlation_id")))
                .as("exactly one facade event carries the incident id as its correlation_id")
                .hasSize(1)
                .first()
                .satisfies(event ->
                        assertThat(event.getThrowableProxy().getMessage()).contains(BoomController.SENTINEL));
    }
}
