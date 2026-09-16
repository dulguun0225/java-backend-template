package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

/** The context loads against a real PostgreSQL, migrations run, and the liveness probe answers. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApplicationIT {

    @org.springframework.boot.test.web.server.LocalServerPort
    int port;

    @Test
    void healthIsUp() {
        String body = RestClient.create("http://localhost:" + port)
                .get()
                .uri("/actuator/health/liveness")
                .retrieve()
                .body(String.class);
        assertThat(body).contains("UP");
    }
}
