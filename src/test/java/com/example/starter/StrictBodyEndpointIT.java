package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import com.example.starter.platform.error.BoundBody;
import com.example.startertest.StrictBodyProbeController;
import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.RestClient;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The strict-body sweep over every body-taking operation the running service maps, discovered from Spring's own
 * handler mapping rather than listed, so an operation added later is swept without being named here. Each one,
 * called on a path whose values are well formed, answers:
 *
 * <ul>
 *   <li>a member its request type does not declare — 400 {@code validation.failed}, {@code validation.unknown-field}
 *       at its pointer, {@code allowed} the {@code properties} of the operation's request schema in the generated
 *       document, sorted; 101 of them — 100 entries and {@code errorsOmitted: 1};
 *   <li>each of its path variables sent in the body — {@code validation.identifier-in-path} at that pointer;
 *   <li>every member of its record sent as a JSON array — {@code validation.wrong-type} at that pointer, with its
 *       {@code expected} param, the member's schema {@code type}, and a {@code detail};
 *   <li>every member of its record given twice, and a member given twice inside an undeclared object —
 *       {@code validation.duplicate-member} at the repeated member's pointer;
 *   <li>text that is not well-formed JSON — 400 {@code validation.malformed-body} with the line and column;
 *   <li>no body, where the body is required — 400 {@code validation.malformed-body}, "The request body is
 *       missing.";
 * </ul>
 *
 * <p>and none of them opens a transaction, so the sweep writes nothing. A sentinel sent as a value in those bodies
 * appears in no response and in no log line. The main operations discovered are held equal to the operations the
 * committed {@code openapi/v1.json} declares a request body for, both ways. The schemas are read from the document
 * this context generates, which carries the probe's operations beside the main ones; {@code OpenApiSnapshotIT}
 * holds the main ones equal to the committed document.
 *
 * <p>{@link StrictBodyProbeController} is imported beside the main controllers: its {@code update} keeps the
 * path-identifier case non-vacuous while no main operation takes a body on a route with a variable, and its
 * {@code lenient} handler, which binds its body raw, is the negative control — the same undeclared member that
 * every swept operation refuses is accepted there, so the refusal is the strict reader's doing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({
    TestcontainersConfiguration.class,
    StrictBodyProbeController.class,
    StrictBodyEndpointIT.TransactionCounting.class
})
class StrictBodyEndpointIT {

    private static final String SENTINEL = "SENTINEL-c41e9b";
    private static final String PATH_VALUE = "PRB";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicLong TRANSACTIONS = new AtomicLong();

    @LocalServerPort
    int port;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapping;

    private final CapturingAppender appender = new CapturingAppender();

    private record BodyOperation(
            String operationId,
            HttpMethod method,
            String pattern,
            List<String> pathVariables,
            boolean required,
            Class<?> body,
            boolean main) {}

    /** Counts every transaction the service opens: every statement runs inside one ({@code Tx}). */
    @TestConfiguration(proxyBeanMethods = false)
    static class TransactionCounting {

        @Bean
        static BeanPostProcessor countTransactions() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof PlatformTransactionManager)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(
                            bean.getClass().getClassLoader(),
                            ClassUtils.getAllInterfaces(bean),
                            (proxy, method, args) -> {
                                if (method.getName().equals("getTransaction")) {
                                    TRANSACTIONS.incrementAndGet();
                                }
                                try {
                                    return method.invoke(bean, args);
                                } catch (java.lang.reflect.InvocationTargetException e) {
                                    throw Objects.requireNonNull(e.getCause());
                                }
                            });
                }
            };
        }
    }

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
    void theDiscoveredOperationsAreExactlyTheOnesTheContractDeclaresABodyFor() throws IOException {
        Set<String> discovered = operations().stream()
                .filter(BodyOperation::main)
                .map(BodyOperation::operationId)
                .collect(Collectors.toCollection(TreeSet::new));
        JsonNode paths = JSON.readTree(Files.readString(OpenApiSnapshotIT.COMMITTED, StandardCharsets.UTF_8))
                .get("paths");
        Set<String> declared = new TreeSet<>();
        for (Map.Entry<String, JsonNode> path : paths.properties()) {
            for (Map.Entry<String, JsonNode> operation : path.getValue().properties()) {
                if (operation.getValue().has("requestBody")) {
                    declared.add(operation.getValue().get("operationId").asString());
                }
            }
        }
        assertThat(discovered).as("the sweep found main body-taking operations").isNotEmpty();
        assertThat(discovered).isEqualTo(declared);
    }

    @Test
    void anUndeclaredMemberIsNamedOnEveryOperationListingTheSchemasMembersAndOpensNoTransaction() {
        JsonNode document = document();
        for (BodyOperation operation : operations()) {
            ResponseEntity<String> refused =
                    refusedWithoutATransaction(operation, "{\"__probe\":\"" + SENTINEL + "\"}");
            List<String> members = new ArrayList<>();
            requestSchema(document, operation).get("properties").propertyNames().forEach(members::add);
            assertThat(members)
                    .as(operation.operationId() + " declares members")
                    .isNotEmpty();
            assertThat(errorsOf(refused))
                    .as(operation.operationId())
                    .contains(Map.of(
                            "pointer",
                            "/__probe",
                            "code",
                            "validation.unknown-field",
                            "params",
                            Map.of("allowed", members.stream().sorted().toList())));
            assertNoEcho(operation, refused);
        }
    }

    /**
     * One entry per undeclared member would let a body buy a response many times its size: 101 binding failures are
     * 100 entries and {@code errorsOmitted: 1}. Each declared member is sent as a JSON array, so the field rules,
     * whose failures at a member already refused are dropped, add none, and the 101 are binding failures alone.
     */
    @Test
    void oneHundredAndOneFailuresAreOneHundredEntriesAndOneOmittedOnEveryOperation() {
        for (BodyOperation operation : operations()) {
            RecordComponent[] members = operation.body().getRecordComponents();
            String body = java.util.stream.Stream.concat(
                            java.util.Arrays.stream(members).map(member -> "\"" + member.getName() + "\":[]"),
                            java.util.stream.IntStream.range(0, 101 - members.length)
                                    .mapToObj(i -> "\"__m" + (1000 + i) + "\":0"))
                    .collect(Collectors.joining(",", "{", "}"));
            Map<String, Object> problem = bodyOf(refusedWithoutATransaction(operation, body));
            assertThat(problem).as(operation.operationId()).containsEntry("errorsOmitted", 1);
            assertThat(errorsOf(problem)).as(operation.operationId()).hasSize(100);
        }
    }

    @Test
    void everyPathVariableSentInTheBodyIsAnIdentifierInThePathOnEveryOperation() {
        int identifiers = 0;
        for (BodyOperation operation : operations()) {
            for (String variable : operation.pathVariables()) {
                ResponseEntity<String> refused =
                        refusedWithoutATransaction(operation, "{\"" + variable + "\":\"" + PATH_VALUE + "\"}");
                assertThat(errorsOf(refused))
                        .as(operation.operationId() + " /" + variable)
                        .contains(Map.of("pointer", "/" + variable, "code", "validation.identifier-in-path"));
                identifiers++;
            }
        }
        assertThat(identifiers).as("the sweep sent path identifiers").isPositive();
    }

    @Test
    void everyMemberOfTheWrongJsonTypeIsNamedOnEveryOperationExpectingItsSchemaType() {
        JsonNode document = document();
        for (BodyOperation operation : operations()) {
            JsonNode properties = requestSchema(document, operation).get("properties");
            for (RecordComponent member : operation.body().getRecordComponents()) {
                String type = properties.get(member.getName()).get("type").asString();
                ResponseEntity<String> refused =
                        refusedWithoutATransaction(operation, "{\"" + member.getName() + "\":[\"" + SENTINEL + "\"]}");
                assertThat(errorsOf(refused))
                        .as(operation.operationId() + " /" + member.getName())
                        .anySatisfy(error -> assertThat(error)
                                .containsEntry("pointer", "/" + member.getName())
                                .containsEntry("code", "validation.wrong-type")
                                .containsEntry("detail", "expected " + type)
                                .containsEntry("params", Map.of("expected", type)));
                assertNoEcho(operation, refused);
            }
        }
    }

    /** {@code null} fits every member's type, so the repeat is the only failure the body has. */
    @Test
    void everyMemberGivenTwiceIsADuplicateOnEveryOperationAtAnyDepth() {
        int members = 0;
        for (BodyOperation operation : operations()) {
            for (RecordComponent member : operation.body().getRecordComponents()) {
                String name = member.getName();
                ResponseEntity<String> refused =
                        refusedWithoutATransaction(operation, "{\"" + name + "\":null,\"" + name + "\":null}");
                assertThat(errorsOf(refused))
                        .as(operation.operationId() + " /" + name)
                        .containsExactly(Map.of("pointer", "/" + name, "code", "validation.duplicate-member"));
                members++;
            }
            ResponseEntity<String> nested = refusedWithoutATransaction(
                    operation, "{\"__probe\":{\"a\":\"" + SENTINEL + "\",\"a\":\"" + SENTINEL + "\"}}");
            assertThat(errorsOf(nested))
                    .as(operation.operationId() + " nested")
                    .contains(Map.of("pointer", "/__probe/a", "code", "validation.duplicate-member"));
            assertNoEcho(operation, nested);
        }
        assertThat(members).as("the sweep sent members twice").isPositive();
    }

    @Test
    void textThatIsNotJsonIsMalformedWithItsPositionOnEveryOperation() {
        for (BodyOperation operation : operations()) {
            ResponseEntity<String> refused = refusedWithoutATransaction(operation, "{\"a\":\"" + SENTINEL);
            Map<String, Object> problem = bodyOf(refused);
            assertThat(problem).as(operation.operationId()).containsEntry("code", "validation.malformed-body");
            assertThat(String.valueOf(problem.get("detail")))
                    .as(operation.operationId())
                    .matches("The request body is not well-formed JSON at line 1, column \\d+\\.");
            assertNoEcho(operation, refused);
        }
    }

    @Test
    void anAbsentRequiredBodyIsMalformedSayingSoOnEveryOperation() {
        int required = 0;
        for (BodyOperation operation : operations()) {
            if (!operation.required()) {
                continue;
            }
            required++;
            ResponseEntity<String> refused = refusedWithoutATransaction(operation, null);
            assertThat(bodyOf(refused))
                    .as(operation.operationId())
                    .containsEntry("code", "validation.malformed-body")
                    .containsEntry("detail", "The request body is missing.");
        }
        assertThat(required).isPositive();
    }

    /**
     * The negative control: a body bound raw is read by the default converter, which drops the same member, and
     * the handler's one read transaction is counted, so a zero count elsewhere is a measurement.
     */
    @Test
    void theSameUndeclaredMemberIsAcceptedWhereTheBodyIsBoundRaw() {
        long before = TRANSACTIONS.get();
        ResponseEntity<String> accepted = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build()
                .post()
                .uri(StrictBodyProbeController.LENIENT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"n\",\"__probe\":\"" + SENTINEL + "\"}")
                .retrieve()
                .toEntity(String.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(TRANSACTIONS.get() - before).as("the counter counts").isEqualTo(1);
        assertThat(operations())
                .as("the raw-bound handler is not swept")
                .noneMatch(operation -> operation.pattern().equals(StrictBodyProbeController.LENIENT_PATH));
    }

    /** Sends {@code json} (none when {@code null}) and asserts a 400 reached with no transaction opened. */
    private ResponseEntity<String> refusedWithoutATransaction(BodyOperation operation, @Nullable String json) {
        long before = TRANSACTIONS.get();
        ResponseEntity<String> response = send(operation, json);
        assertThat(response.getStatusCode())
                .as(operation.operationId() + " " + json + ": " + response.getBody())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(TRANSACTIONS.get() - before)
                .as(operation.operationId() + " " + json + ": a refused body opens no transaction")
                .isZero();
        return response;
    }

    private void assertNoEcho(BodyOperation operation, ResponseEntity<String> response) {
        assertThat(Objects.requireNonNull(response.getBody()))
                .as(operation.operationId() + ": the value sent is never echoed")
                .doesNotContain(SENTINEL);
        assertThat(appender.events)
                .as(operation.operationId() + ": the value sent reaches no log line")
                .noneSatisfy(event -> assertThat(event.getFormattedMessage()
                                + (event.getThrowableProxy() == null
                                        ? ""
                                        : event.getThrowableProxy().getMessage()))
                        .contains(SENTINEL));
    }

    private ResponseEntity<String> send(BodyOperation operation, @Nullable String json) {
        Map<String, String> values =
                operation.pathVariables().stream().collect(Collectors.toMap(name -> name, name -> PATH_VALUE));
        String uri = UriComponentsBuilder.fromPath(operation.pattern())
                .buildAndExpand(values)
                .encode()
                .toUriString();
        RestClient.RequestBodySpec spec = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build()
                .method(operation.method())
                .uri(uri)
                .header("If-Match", "\"1\"");
        if (json != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(json);
        }
        return spec.retrieve().toEntity(String.class);
    }

    /**
     * Every handler the running service maps whose {@code @RequestBody} binds as {@code BoundBody}, main or
     * imported probe alike. The path variables are the route template's own, not the handler's
     * {@code @PathVariable} parameters, since those are what the strict reader compares against.
     */
    private List<BodyOperation> operations() {
        List<BodyOperation> operations = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
                mapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            MethodParameter body = null;
            for (MethodParameter parameter : handler.getMethodParameters()) {
                if (parameter.hasParameterAnnotation(RequestBody.class)
                        && parameter.getParameterType() == BoundBody.class) {
                    body = parameter;
                }
            }
            if (body == null) {
                continue;
            }
            RequestMappingInfo info = entry.getKey();
            String pattern = Objects.requireNonNull(info.getPathPatternsCondition())
                    .getPatternValues()
                    .iterator()
                    .next();
            Operation annotation = handler.getMethod().getAnnotation(Operation.class);
            String packageName = handler.getBeanType().getPackageName();
            operations.add(new BodyOperation(
                    annotation != null && !annotation.operationId().isEmpty()
                            ? annotation.operationId()
                            : handler.getMethod().getName(),
                    HttpMethod.valueOf(info.getMethodsCondition()
                            .getMethods()
                            .iterator()
                            .next()
                            .name()),
                    pattern,
                    new UriTemplate(pattern).getVariableNames(),
                    Objects.requireNonNull(body.getParameterAnnotation(RequestBody.class))
                            .required(),
                    (Class<?>) ((ParameterizedType) body.getGenericParameterType()).getActualTypeArguments()[0],
                    packageName.equals(BanListArchTest.BASE) || packageName.startsWith(BanListArchTest.BASE + ".")));
        }
        return operations;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> bodyOf(ResponseEntity<String> response) {
        return JSON.readValue(Objects.requireNonNull(response.getBody()), Map.class);
    }

    private static List<Map<String, Object>> errorsOf(ResponseEntity<String> response) {
        return errorsOf(bodyOf(response));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> errorsOf(Map<String, Object> problem) {
        Object errors = problem.get("errors");
        assertThat(errors).as(String.valueOf(problem)).isInstanceOf(List.class);
        return (List<Map<String, Object>>) Objects.requireNonNull(errors);
    }

    /** The document this context generates: the main operations and the imported probe's. */
    private JsonNode document() {
        String raw = RestClient.create("http://localhost:" + port)
                .get()
                .uri("/v3/api-docs")
                .retrieve()
                .body(String.class);
        return JSON.readTree(Objects.requireNonNull(raw));
    }

    /** The request-body schema the document declares for {@code operation}, its component reference followed. */
    private static JsonNode requestSchema(JsonNode document, BodyOperation operation) {
        JsonNode schema = document.get("paths")
                .get(operation.pattern())
                .get(operation.method().name().toLowerCase(java.util.Locale.ROOT))
                .get("requestBody")
                .get("content")
                .get("application/json")
                .get("schema");
        if (schema.has("$ref")) {
            String ref = schema.get("$ref").asString();
            return document.get("components").get("schemas").get(ref.substring(ref.lastIndexOf('/') + 1));
        }
        return schema;
    }
}
