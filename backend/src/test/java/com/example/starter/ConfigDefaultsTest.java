package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Config-default assertions: the committed {@code application.yaml} carries the properties the skills require.
 * This reads the checked-in default only; an environment override in a deployed process is outside its reach,
 * and that limit is recorded in docs/GATES.md rather than papered over.
 */
class ConfigDefaultsTest {

    @Test
    void committedDefaultsHoldTheRequiredProperties() throws Exception {
        Map<String, Object> root;
        try (InputStream in = ConfigDefaultsTest.class.getResourceAsStream("/application.yaml")) {
            assertThat(in).as("application.yaml on the classpath").isNotNull();
            root = new Yaml().load(in);
        }
        assertThat(at(root, "spring", "threads", "virtual", "enabled"))
                .as("one virtual thread per task")
                .isEqualTo(true);
        assertThat(at(root, "spring", "main", "keep-alive"))
                .as("keep-alive safeguard")
                .isEqualTo(true);
        assertThat(at(root, "spring", "flyway", "enabled")).isEqualTo(true);
        assertThat(at(root, "logging", "structured", "format", "console"))
                .as("structured JSON on stdout")
                .isEqualTo("ecs");
        Object poolSize = at(root, "spring", "datasource", "hikari", "maximum-pool-size");
        assertThat(poolSize)
                .as("the pool is the DB semaphore: small, fixed, committed")
                .isInstanceOf(Integer.class);
        assertThat((Integer) poolSize).isBetween(2, 32);
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Object at(Map<String, Object> root, String... path) {
        Map<String, Object> current = root;
        for (int i = 0; i < path.length - 1; i++) {
            Object next = current.get(path[i]);
            assertThat(next).as("missing key " + path[i]).isInstanceOf(Map.class);
            current = (Map<String, Object>) Objects.requireNonNull(next);
        }
        return current.get(path[path.length - 1]);
    }
}
