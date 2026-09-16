package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * One committed OpenAPI document, generated from the code and diffed. springdoc renders {@code /v3/api-docs};
 * this test passes it through the hand-owned normalizer (recursive key sort, two-space indent, LF, trailing
 * newline, the per-run {@code servers} entry removed) and compares the bytes to {@code openapi/v1.json}. A
 * change to the contract fails the build until the committed document is deliberately updated. The
 * generator's own ordering is not trusted. CI runs this a second time under another timezone and locale to
 * prove the bytes do not depend on the machine.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OpenApiSnapshotIT {

    static final Path COMMITTED = Path.of("openapi", "v1.json");

    @LocalServerPort
    int port;

    @Test
    void openApiDocumentMatchesTheCommittedNormalizedCopy() throws IOException {
        String raw = RestClient.create("http://localhost:" + port)
                .get()
                .uri("/v3/api-docs")
                .retrieve()
                .body(String.class);
        assertThat(raw).isNotBlank();
        String normalized = normalize(Objects.requireNonNull(raw));

        String committed = Files.exists(COMMITTED)
                ? Files.readString(COMMITTED, StandardCharsets.UTF_8).replace("\r\n", "\n")
                : "";
        if (!normalized.equals(committed)) {
            Files.createDirectories(Path.of("target"));
            Files.writeString(Path.of("target", "openapi-v1.actual.json"), normalized, StandardCharsets.UTF_8);
        }
        assertThat(normalized)
                .as("OpenAPI drift: the generated contract no longer matches " + COMMITTED
                        + ". Review the diff, then copy target/openapi-v1.actual.json over it")
                .isEqualTo(committed);
    }

    /** The canonical form. Owned here, not delegated to the generator's ordering option. */
    static String normalize(String json) {
        ObjectMapper mapper = JsonMapper.builder()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
        JsonNode tree = mapper.readTree(json);
        if (tree instanceof ObjectNode object) {
            object.remove("servers");
        }
        JsonNode sorted = mapper.convertValue(mapper.convertValue(tree, java.util.TreeMap.class), JsonNode.class);
        return mapper.writeValueAsString(sorted).replace("\r\n", "\n").stripTrailing() + "\n";
    }
}
