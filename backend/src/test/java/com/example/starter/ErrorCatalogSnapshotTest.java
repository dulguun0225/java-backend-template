package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.starter.greeting.GreetingErrorCode;
import com.example.starter.greeting.GreetingFieldCode;
import com.example.starter.platform.error.FieldCode;
import com.example.starter.platform.error.WireError;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The committed wire-contract snapshot for the error catalogs: a new or moved wire code or a changed HTTP
 * status shows up as a reviewable diff and fails the build until {@code error-catalog-snapshot.txt} is
 * deliberately updated. Three guards, no Spring context: the snapshot itself; one wire string maps to one
 * status across every catalog; and the explicit catalog lists here equal the set of {@link WireError} and
 * {@link FieldCode} enums on the classpath, so a new feature's catalog cannot be silently left out.
 */
class ErrorCatalogSnapshotTest {

    private static final String CATALOG_RESOURCE = "/error-catalog-snapshot.txt";

    /** Every {@link WireError} enum. Add a feature's catalog here; the classpath reconciliation demands it. */
    private static final List<Class<? extends WireError>> RESPONSE_CATALOGS =
            List.of(ApiErrorCode.class, GreetingErrorCode.class);

    /** Every {@link FieldCode} enum. */
    private static final List<Class<? extends FieldCode>> FIELD_CATALOGS = List.of(GreetingFieldCode.class);

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BanListArchTest.BASE);

    @Test
    void errorCatalogMatchesTheCommittedSnapshot() throws IOException {
        List<String> lines = new ArrayList<>();
        for (Class<? extends WireError> catalog : RESPONSE_CATALOGS) {
            for (WireError code : constants(catalog)) {
                lines.add(code.wire() + " -> " + code.status() + " (" + catalog.getSimpleName() + "."
                        + ((Enum<?>) code).name() + ")");
            }
        }
        for (Class<? extends FieldCode> catalog : FIELD_CATALOGS) {
            for (FieldCode code : constants(catalog)) {
                lines.add(code.wire() + " -> - (" + catalog.getSimpleName() + "." + ((Enum<?>) code).name() + ")");
            }
        }
        assertThat(lines).isNotEmpty();
        assertSnapshot(render(lines), CATALOG_RESOURCE, "error-catalog-snapshot");
    }

    @Test
    void everyWireMapsToExactlyOneStatusAcrossCatalogs() {
        Map<String, Set<Integer>> statusesByWire = new HashMap<>();
        for (Class<? extends WireError> catalog : RESPONSE_CATALOGS) {
            for (WireError code : constants(catalog)) {
                statusesByWire
                        .computeIfAbsent(code.wire(), w -> new TreeSet<>())
                        .add(code.status());
            }
        }
        List<String> divergent = statusesByWire.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> e.getKey() + " -> " + e.getValue())
                .sorted()
                .toList();
        assertThat(divergent)
                .as("a wire code must map to one HTTP status across every catalog; divergent: " + divergent)
                .isEmpty();
    }

    @Test
    void responseCatalogListEqualsTheClasspath() {
        assertScanEqualsList(WireError.class, RESPONSE_CATALOGS, "WireError");
    }

    @Test
    void fieldCatalogListEqualsTheClasspath() {
        assertScanEqualsList(FieldCode.class, FIELD_CATALOGS, "FieldCode");
    }

    private static <T> T[] constants(Class<T> catalog) {
        T[] constants = catalog.getEnumConstants();
        assertThat(constants)
                .as("catalog " + catalog.getName() + " must be an enum")
                .isNotNull();
        return constants;
    }

    private static void assertScanEqualsList(Class<?> contract, List<? extends Class<?>> listed, String label) {
        Set<String> scanned = MAIN.stream()
                .filter(JavaClass::isEnum)
                .filter(c -> c.isAssignableTo(contract))
                .map(JavaClass::getName)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> declared = listed.stream().map(Class::getName).collect(Collectors.toCollection(TreeSet::new));
        assertThat(scanned)
                .as("classpath scan found no " + label + " enums; the importer is broken")
                .isNotEmpty();
        assertThat(scanned)
                .as("the explicit " + label + " catalog list must equal the enums implementing " + label
                        + " on the classpath. Add the missing class to the list in ErrorCatalogSnapshotTest.")
                .isEqualTo(declared);
    }

    private static String render(List<String> lines) {
        return lines.stream().sorted().collect(Collectors.joining("\n", "", "\n"));
    }

    private static void assertSnapshot(String actual, String resource, String name) throws IOException {
        String committed = readCommittedSnapshot(resource);
        if (!actual.equals(committed)) {
            Files.createDirectories(Path.of("target"));
            Files.writeString(Path.of("target", name + ".actual.txt"), actual, StandardCharsets.UTF_8);
        }
        assertThat(actual)
                .as("error catalog snapshot drift: the live catalog no longer matches " + resource
                        + ". Review the change, then copy target/" + name + ".actual.txt over src/test/resources"
                        + resource
                        + ". Live snapshot:\n" + actual)
                .isEqualTo(committed);
    }

    private static String readCommittedSnapshot(String resource) throws IOException {
        try (InputStream in = ErrorCatalogSnapshotTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
