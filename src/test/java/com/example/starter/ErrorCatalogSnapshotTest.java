package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.starter.greeting.GreetingErrorCode;
import com.example.starter.greeting.GreetingFieldCode;
import com.example.starter.platform.error.FieldCode;
import com.example.starter.platform.error.FieldParams;
import com.example.starter.platform.error.ProblemParams;
import com.example.starter.platform.error.WireError;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The committed wire-contract snapshot for the error catalogs: every {@code (code, HTTP status, param names)}
 * triple, so a new or moved wire code, a changed HTTP status or a param added, removed or renamed shows up as a
 * reviewable diff and fails the build until {@code error-catalog-snapshot.txt} is deliberately updated. A code's
 * list is the components of its params record, in order: a field code's {@link FieldParams} record, a response
 * code's {@link ProblemParams} record ({@code max} on {@code request.too-large}), or {@code []} for a response code
 * that keeps {@link WireError.NoParams}. The guards, no Spring context: the snapshot itself; one wire string maps
 * to one status and one param list across every response catalog and to one param list across every field
 * catalog; every param name is one lower-case word; the explicit catalog lists here equal the set of
 * {@link WireError} and {@link FieldCode} enums on the classpath, so a new feature's catalog cannot be silently
 * left out; and every code with params and its params record name each other, one to one, with no params record
 * in the main code that no code declares. A code cannot be raised without its params: {@code FieldError.of} and
 * the edge's problem builder take the code from the record, {@code Rejected} refuses a code with params, and
 * {@code BanListArchTest.fieldErrorsAreBuiltFromTheirParams} keeps main code off the constructors that take both.
 */
class ErrorCatalogSnapshotTest {

    private static final String CATALOG_RESOURCE = "/error-catalog-snapshot.txt";

    /** A param name is one lower-case word: {@code max}, {@code expected}, {@code allowed}, never {@code maxBytes}. */
    private static final Pattern PARAM_NAME = Pattern.compile("[a-z]+");

    /** Every {@link WireError} enum. Add a feature's catalog here; the classpath reconciliation demands it. */
    private static final List<Class<? extends WireError>> RESPONSE_CATALOGS =
            List.of(ApiErrorCode.class, GreetingErrorCode.class);

    /**
     * Every {@link FieldCode} enum. The cross-cutting request-input codes ({@code validation.unknown-field},
     * {@code validation.invalid-value} and the rest) stay in {@link ApiFieldCode}, raised by the strict body reader
     * and the edge in the base package: a feature catalog does not restate them, except {@code validation.required},
     * which a feature's rules raise too, under the same params.
     */
    private static final List<Class<? extends FieldCode>> FIELD_CATALOGS =
            List.of(ApiFieldCode.class, GreetingFieldCode.class);

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BanListArchTest.BASE);

    @Test
    void errorCatalogMatchesTheCommittedSnapshot() throws IOException {
        List<String> lines = new ArrayList<>();
        for (Class<? extends WireError> catalog : RESPONSE_CATALOGS) {
            for (WireError code : constants(catalog)) {
                lines.add(code.wire() + " -> " + code.status() + " [" + String.join(", ", paramNames(code.paramsType()))
                        + "] (" + catalog.getSimpleName() + "." + ((Enum<?>) code).name() + ")");
            }
        }
        for (Class<? extends FieldCode> catalog : FIELD_CATALOGS) {
            for (FieldCode code : constants(catalog)) {
                lines.add(code.wire() + " -> - [" + String.join(", ", paramNames(code)) + "] ("
                        + catalog.getSimpleName() + "." + ((Enum<?>) code).name() + ")");
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
    void everyResponseWireCarriesOneParamListAcrossCatalogs() {
        Map<String, Set<List<String>>> paramsByWire = new HashMap<>();
        for (Class<? extends WireError> catalog : RESPONSE_CATALOGS) {
            for (WireError code : constants(catalog)) {
                paramsByWire
                        .computeIfAbsent(code.wire(), w -> new java.util.HashSet<>())
                        .add(paramNames(code.paramsType()));
            }
        }
        assertThat(paramsByWire.entrySet().stream()
                        .filter(e -> e.getValue().size() > 1)
                        .map(e -> e.getKey() + " -> " + e.getValue())
                        .sorted()
                        .toList())
                .as("a response code must carry the same params in every catalog that declares it")
                .isEmpty();
    }

    @Test
    void everyParamNameIsOneLowerCaseWord() {
        List<String> names = new ArrayList<>();
        for (Class<? extends WireError> catalog : RESPONSE_CATALOGS) {
            for (WireError code : constants(catalog)) {
                paramNames(code.paramsType()).forEach(name -> names.add(code.wire() + "." + name));
            }
        }
        for (Class<? extends FieldCode> catalog : FIELD_CATALOGS) {
            for (FieldCode code : constants(catalog)) {
                paramNames(code).forEach(name -> names.add(code.wire() + "." + name));
            }
        }
        assertThat(names).as("the catalogs declare params").isNotEmpty();
        assertThat(names)
                .as("a param name is one lower-case word, so both templates spell it alike")
                .allSatisfy(name -> assertThat(PARAM_NAME
                                .matcher(name.substring(name.lastIndexOf('.') + 1))
                                .matches())
                        .as(name)
                        .isTrue());
    }

    /**
     * A response code with params names a {@link ProblemParams} record of its own, and the record names the code
     * back; a code without params keeps {@link WireError.NoParams}.
     */
    @Test
    void everyResponseCodeWithParamsAndItsRecordNameEachOther() throws ReflectiveOperationException {
        Map<Class<?>, String> declaredBy = new HashMap<>();
        for (Class<? extends WireError> catalog : RESPONSE_CATALOGS) {
            for (WireError code : constants(catalog)) {
                String constant = catalog.getSimpleName() + "." + ((Enum<?>) code).name();
                Class<? extends Record> type = code.paramsType();
                if (type == WireError.NoParams.class) {
                    continue;
                }
                assertThat(ProblemParams.class.isAssignableFrom(type))
                        .as(constant + " params type " + type.getName() + " must be a ProblemParams record")
                        .isTrue();
                assertThat(type.getRecordComponents())
                        .as(constant + " names a params record with no params; it keeps WireError.NoParams")
                        .isNotEmpty();
                assertThat(declaredBy.put(type, constant))
                        .as(type.getName() + " is the params record of two codes")
                        .isNull();
                assertThat(((ProblemParams) instantiate(type)).code())
                        .as(type.getName() + ".code() must be " + constant)
                        .isSameAs(code);
            }
        }
        assertThat(declaredBy).as("a response code carries params").isNotEmpty();
    }

    @Test
    void everyProblemParamsRecordInTheMainCodeIsDeclaredByAResponseCode() {
        Set<String> scanned = MAIN.stream()
                .filter(c -> !c.isInterface())
                .filter(c -> c.isAssignableTo(ProblemParams.class))
                .map(JavaClass::getName)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> declared = RESPONSE_CATALOGS.stream()
                .flatMap(catalog -> Arrays.stream(constants(catalog)))
                .map(code -> code.paramsType().getName())
                .filter(name -> !name.equals(WireError.NoParams.class.getName()))
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(scanned)
                .as("every ProblemParams implementation must be the params record of exactly one response code")
                .isEqualTo(declared);
    }

    @Test
    void everyFieldWireCarriesOneParamListAcrossCatalogs() {
        Map<String, Set<List<String>>> paramsByWire = new HashMap<>();
        for (Class<? extends FieldCode> catalog : FIELD_CATALOGS) {
            for (FieldCode code : constants(catalog)) {
                paramsByWire
                        .computeIfAbsent(code.wire(), w -> new java.util.HashSet<>())
                        .add(paramNames(code));
            }
        }
        List<String> divergent = paramsByWire.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> e.getKey() + " -> " + e.getValue())
                .sorted()
                .toList();
        assertThat(divergent)
                .as("a field code must carry the same params in every catalog that declares it; divergent: "
                        + divergent)
                .isEmpty();
    }

    /**
     * Each field code's params type is a record of its own, and that record names the code back: built with zero
     * and {@code null} arguments, which no params record refuses, its {@code code()} is the constant declaring it.
     */
    @Test
    void everyFieldCodeAndItsParamsRecordNameEachOther() throws ReflectiveOperationException {
        Map<Class<?>, String> declaredBy = new HashMap<>();
        for (Class<? extends FieldCode> catalog : FIELD_CATALOGS) {
            for (FieldCode code : constants(catalog)) {
                String constant = catalog.getSimpleName() + "." + ((Enum<?>) code).name();
                Class<? extends FieldParams> type = code.paramsType();
                assertThat(type.isRecord())
                        .as(constant + " params type " + type.getName() + " must be a record")
                        .isTrue();
                assertThat(declaredBy.put(type, constant))
                        .as(type.getName() + " is the params record of two codes")
                        .isNull();
                assertThat(instantiate(type).code())
                        .as(type.getName() + ".code() must be " + constant)
                        .isSameAs(code);
            }
        }
    }

    @Test
    void everyParamsRecordInTheMainCodeIsDeclaredByAFieldCode() {
        Set<String> scanned = MAIN.stream()
                .filter(c -> !c.isInterface())
                .filter(c -> c.isAssignableTo(FieldParams.class))
                .map(JavaClass::getName)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> declared = FIELD_CATALOGS.stream()
                .flatMap(catalog -> Arrays.stream(constants(catalog)))
                .map(code -> code.paramsType().getName())
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(scanned).as("classpath scan found no FieldParams records").isNotEmpty();
        assertThat(scanned)
                .as("every FieldParams implementation must be the params record of exactly one field code")
                .isEqualTo(declared);
    }

    @Test
    void responseCatalogListEqualsTheClasspath() {
        assertScanEqualsList(WireError.class, RESPONSE_CATALOGS, "WireError");
    }

    @Test
    void fieldCatalogListEqualsTheClasspath() {
        assertScanEqualsList(FieldCode.class, FIELD_CATALOGS, "FieldCode");
    }

    private static List<String> paramNames(FieldCode code) {
        return paramNames(code.paramsType());
    }

    private static List<String> paramNames(Class<?> paramsType) {
        RecordComponent[] components = paramsType.getRecordComponents();
        assertThat(components).as(paramsType.getName() + " must be a record").isNotNull();
        return Arrays.stream(components).map(RecordComponent::getName).toList();
    }

    private static <T> T instantiate(Class<? extends T> type) throws ReflectiveOperationException {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] types =
                Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] arguments =
                Arrays.stream(types).map(ErrorCatalogSnapshotTest::zero).toArray();
        Constructor<? extends T> canonical = type.getDeclaredConstructor(types);
        try {
            return canonical.newInstance(arguments);
        } catch (InvocationTargetException e) {
            throw new AssertionError(type.getName() + " refused zero and null arguments", e.getCause());
        }
    }

    private static @org.jspecify.annotations.Nullable Object zero(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type.isPrimitive()) {
            return java.lang.reflect.Array.get(java.lang.reflect.Array.newInstance(type, 1), 0);
        }
        return null;
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
