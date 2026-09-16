package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Two bans that no bytecode rule can see, as a file-content gate over the build and deploy files: no Java agent
 * (bytecode weaving; instrumentation is visible SDK-level code) and no preview-feature flag (a preview API is a
 * moving target and StructuredTaskScope is categorically out). The same grep runs in CI as a shell step over a
 * wider file set; this test keeps the verdict inside the build too.
 */
class ForbiddenFlagsTest {

    private static final List<String> BANNED_TOKENS = List.of(
            "-javaagent",
            "opentelemetry-javaagent",
            "otel-javaagent",
            "aws-opentelemetry-agent",
            "--enable" + "-preview");

    @Test
    void noAgentOrPreviewFlagInBuildOrDeployFiles() throws IOException {
        Path backend = Path.of("").toAbsolutePath(); // surefire cwd = backend/
        Path root = backend.resolve("..").normalize();

        List<Path> files = new ArrayList<>();
        addIfExists(files, backend.resolve("Dockerfile"));
        addIfExists(files, root.resolve("compose.yaml"));
        addIfExists(files, backend.resolve(".mvn/jvm.config"));
        addIfExists(files, backend.resolve("pom.xml"));
        assertThat(files)
                .as("the scan found too few files; check the Dockerfile, compose, jvm.config and pom locations")
                .anyMatch(p -> p.getFileName().toString().equals("Dockerfile"))
                .anyMatch(p -> p.getFileName().toString().equals("compose.yaml"))
                .anyMatch(p -> p.getFileName().toString().equals("jvm.config"))
                .hasSizeGreaterThanOrEqualTo(4);

        for (Path file : files) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for (String token : BANNED_TOKENS) {
                assertThat(content).as(file + " must not contain " + token).doesNotContain(token);
            }
        }
    }

    private static void addIfExists(List<Path> files, Path candidate) {
        if (Files.exists(candidate)) {
            files.add(candidate);
        }
    }
}
