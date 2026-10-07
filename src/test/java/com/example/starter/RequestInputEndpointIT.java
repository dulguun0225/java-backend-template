package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.startertest.InputProbeController;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A path variable, query parameter or header that is refused is named by OpenAPI's parameter identity, {@code in}
 * and {@code name}, with the code and params its kind of refusal carries: absent ({@code validation.required}),
 * blank or not parsing ({@code validation.invalid-value}, {@code expected} the schema's format), outside its set
 * ({@code validation.unknown-value}, {@code allowed} its values), repeated ({@code validation.duplicate-member}),
 * undeclared ({@code validation.unknown-field}, {@code allowed} the declared query parameters). Each entry is held
 * against the parameter the generated document declares for the operation: the location and name are the
 * document's, a header's in its declared spelling whatever was sent, and {@code expected} and {@code allowed} are
 * that parameter's schema. An undeclared header is accepted.
 *
 * <p>{@link InputProbeController} supplies the inputs; its {@code lenient} route, which the undeclared-query check
 * does not reach, is the negative control: the same undeclared and repeated query parameters answer 200 there. The
 * requests go through the JDK client directly, so a header can be sent twice and a query string sent exactly as
 * written.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, InputProbeController.class})
class RequestInputEndpointIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ID = "0190f0c4-7d2e-7b3a-9c4d-5e6f7a8b9c0d";
    private static final String SENTINEL = "SENTINEL-5d1c";
    private static final HttpClient CLIENT =
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @LocalServerPort
    int port;

    private record Response(int status, String body) {}

    @Test
    void aRequestEveryInputOfWhichParsesIsAnswered() {
        assertThat(get("?count=-7&colour=GREEN", ID.toUpperCase(java.util.Locale.ROOT)))
                .isEqualTo(new Response(200, "-7 GREEN " + ID));
        assertThat(get("?count=%2B7", ID).status()).isEqualTo(200);
    }

    /** Absent means the name is not in the request at all. */
    @Test
    void anAbsentRequiredInputIsRequiredAtItsLocationAndName() {
        assertThat(entries(get("", ID))).containsExactly(entry("query", "count", "validation.required", null, null));
        assertThat(entries(send("?count=1", List.of())))
                .containsExactly(entry("header", InputProbeController.HEADER, "validation.required", null, null));
    }

    /** A name sent with an empty or blank value is present: its value does not parse. */
    @Test
    void aBlankInputIsAnInvalidValueNamingItsFormat() {
        for (String count : List.of("", "%20", "+")) {
            assertThat(entries(get("?count=" + count, ID)))
                    .as(count)
                    .containsExactly(invalid("query", "count", "int32"));
        }
        assertThat(entries(get("?count=1", "")))
                .containsExactly(invalid("header", InputProbeController.HEADER, "uuid"));
    }

    /**
     * Spring's own conversions trim, read {@code 0x10} as hexadecimal and {@code 1 2} as {@code 12}, and take
     * {@code 1-1-1-1-1} as a UUID; each is refused here. A header is named in the spelling the handler declares,
     * whatever spelling was sent, and no value sent is echoed.
     */
    @Test
    void anInputThatDoesNotParseIsAnInvalidValueNamingItsFormat() {
        for (String count : List.of("abc", "0x10", "%2310", "1%202", "%205", "5%20", "5.0", "3000000000", SENTINEL)) {
            Response response = get("?count=" + count, ID);
            assertThat(entries(response)).as(count).containsExactly(invalid("query", "count", "int32"));
            assertThat(response.body()).doesNotContain(SENTINEL);
        }
        for (String id : List.of(
                "1-1-1-1-1",
                ID.replace("-", ""),
                "{" + ID + "}",
                "urn:uuid:" + ID,
                "AZDwxH0uezqcTV5veouckA==",
                SENTINEL)) {
            Response response = send("?count=1", List.of("x-PROBE-id", id));
            assertThat(entries(response))
                    .as(id)
                    .containsExactly(invalid("header", InputProbeController.HEADER, "uuid"));
            assertThat(response.body()).doesNotContain(SENTINEL);
        }
    }

    /** Spring's own enumeration conversion trims; the exact name is the only value of a constant here. */
    @Test
    void aValueOutsideItsSetIsAnUnknownValueListingTheValues() {
        for (String colour : List.of("PURPLE", "red", "%20RED", "")) {
            assertThat(entries(get("?count=1&colour=" + colour, ID)))
                    .as(colour)
                    .containsExactly(entry(
                            "query",
                            "colour",
                            "validation.unknown-value",
                            Map.of("allowed", List.of("BLUE", "GREEN", "RED")),
                            null));
        }
    }

    /** Spring binds the first of two values for one name, or joins them; neither is used here. */
    @Test
    void aRepeatedQueryParameterOrSingleValuedHeaderIsADuplicate() {
        assertThat(entries(get("?count=1&count=1", ID)))
                .containsExactly(entry("query", "count", "validation.duplicate-member", null, null));
        assertThat(entries(send("?count=1", List.of(InputProbeController.HEADER, ID, "x-probe-id", ID))))
                .containsExactly(
                        entry("header", InputProbeController.HEADER, "validation.duplicate-member", null, null));
        assertThat(entries(get("?count=1&colour=RED&count=2&colour=BLUE", ID)))
                .containsExactly(
                        entry("query", "count", "validation.duplicate-member", null, null),
                        entry("query", "colour", "validation.duplicate-member", null, null));
    }

    /** Spring ignores a query parameter the handler does not declare; a misspelt one is refused here. */
    @Test
    void anUndeclaredQueryParameterIsAnUnknownFieldListingTheDeclaredOnes() {
        Map<String, Object> allowed = Map.of("allowed", List.of("colour", "count"));
        assertThat(entries(get("?count=1&cuont=2", ID)))
                .containsExactly(entry("query", "cuont", "validation.unknown-field", allowed, null));
        assertThat(entries(get("?count=1&a+b&x=1&x=2&%63olour=RED", ID)))
                .containsExactly(
                        entry("query", "a b", "validation.unknown-field", allowed, null),
                        entry("query", "x", "validation.unknown-field", allowed, null),
                        entry("query", "x", "validation.duplicate-member", null, null));
    }

    /**
     * A type the check leaves to Spring's conversion is refused by the edge in the same shape: a text Spring cannot
     * convert, and an empty one Spring converts to no value for a required parameter, are each
     * {@code validation.invalid-value} with the type or format {@code expected}.
     */
    @Test
    void aValueSpringsOwnConversionRefusesIsAnInvalidValueToo() {
        assertThat(entries(
                        InputProbeController.CONVERTED_PATH,
                        send(InputProbeController.CONVERTED_PATH, "?flag=maybe", List.of())))
                .containsExactly(invalid("query", "flag", "boolean"));
        assertThat(entries(
                        InputProbeController.CONVERTED_PATH,
                        send(InputProbeController.CONVERTED_PATH, "?flag=", List.of())))
                .containsExactly(invalid("query", "flag", "boolean"));
        assertThat(entries(
                        InputProbeController.CONVERTED_PATH,
                        send(InputProbeController.CONVERTED_PATH, "?flag=true&on=" + SENTINEL, List.of())))
                .containsExactly(invalid("query", "on", "date"));
        assertThat(entries(
                        InputProbeController.CONVERTED_PATH, send(InputProbeController.CONVERTED_PATH, "", List.of())))
                .containsExactly(entry("query", "flag", "validation.required", null, null));
        assertThat(send(InputProbeController.CONVERTED_PATH, "?flag=true&on=2026-10-07", List.of()))
                .isEqualTo(new Response(200, "true 2026-10-07"));
    }

    /** RFC 9110 §5.1: a recipient ignores a header it does not recognise, and proxies add them. */
    @Test
    void anUndeclaredHeaderIsAccepted() {
        assertThat(send("?count=1", List.of(InputProbeController.HEADER, ID, "X-Undeclared", SENTINEL, "X-Other", "")))
                .isEqualTo(new Response(200, "1 null " + ID));
    }

    /**
     * The negative control: where the undeclared-query check does not reach, Spring answers the same undeclared
     * parameter 200. A repeat of a declared single-valued parameter is refused there too.
     */
    @Test
    void theSameUndeclaredQueryParameterIsAcceptedWhereTheCheckDoesNotReach() {
        assertThat(send(
                        InputProbeController.LENIENT_PATH,
                        "?count=1&cuont=2",
                        List.of(InputProbeController.HEADER, ID)))
                .isEqualTo(new Response(200, "1 " + ID + " GET"));
        assertThat(send(InputProbeController.LENIENT_PATH, "?count=1&count=2", List.of(InputProbeController.HEADER, ID))
                        .status())
                .isEqualTo(400);
    }

    private static Map<String, Object> entry(
            String in, String name, String code, @Nullable Map<String, Object> params, @Nullable String detail) {
        Map<String, Object> entry = new java.util.LinkedHashMap<>();
        entry.put("in", in);
        entry.put("name", name);
        entry.put("code", code);
        if (params != null) {
            entry.put("params", params);
        }
        if (detail != null) {
            entry.put("detail", detail);
        }
        return entry;
    }

    private static Map<String, Object> invalid(String in, String name, String expected) {
        return entry(in, name, "validation.invalid-value", Map.of("expected", expected), "expected " + expected);
    }

    /**
     * The entries of a {@code validation.failed}, each first held against the parameter the generated document
     * declares for the strict operation at its {@code in} and {@code name}: {@code expected} is that parameter's
     * schema format, else its type, and {@code allowed} its enumeration, sorted, or for an undeclared query
     * parameter the operation's query parameters, sorted.
     */
    private List<Map<String, Object>> entries(Response response) {
        return entries(InputProbeController.STRICT_PATH, response);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> entries(String path, Response response) {
        assertThat(response.status()).as(response.body()).isEqualTo(400);
        Map<String, Object> problem = JSON.readValue(response.body(), Map.class);
        assertThat(problem).containsEntry("code", "validation.failed").doesNotContainKey("errorsOmitted");
        List<Map<String, Object>> errors = (List<Map<String, Object>>) Objects.requireNonNull(problem.get("errors"));
        JsonNode parameters = document().get("paths").get(path).get("get").get("parameters");
        List<String> query = new ArrayList<>();
        parameters.forEach(parameter -> {
            if (parameter.get("in").asString().equals("query")) {
                query.add(parameter.get("name").asString());
            }
        });
        for (Map<String, Object> error : errors) {
            assertThat(error).doesNotContainKey("pointer");
            String in = String.valueOf(error.get("in"));
            String name = String.valueOf(error.get("name"));
            Map<String, Object> params = (Map<String, Object>) error.get("params");
            if ("validation.unknown-field".equals(error.get("code"))) {
                assertThat(in).isEqualTo("query");
                assertThat(query).doesNotContain(name);
                assertThat(params)
                        .containsEntry("allowed", query.stream().sorted().toList());
                continue;
            }
            if (in.equals("query") && !query.contains(name)) {
                assertThat(errors)
                        .as("an undeclared name given twice is refused as undeclared too")
                        .anySatisfy(other -> assertThat(other)
                                .containsEntry("name", name)
                                .containsEntry("code", "validation.unknown-field"));
                continue;
            }
            JsonNode declared = null;
            for (JsonNode parameter : parameters) {
                if (parameter.get("in").asString().equals(in)
                        && parameter.get("name").asString().equals(name)) {
                    declared = parameter;
                }
            }
            assertThat(declared).as("the document declares " + in + " " + name).isNotNull();
            JsonNode schema = Objects.requireNonNull(declared).get("schema");
            if (params != null && params.containsKey("expected")) {
                assertThat(params.get("expected"))
                        .isEqualTo(
                                schema.has("format")
                                        ? schema.get("format").asString()
                                        : schema.get("type").asString());
            }
            if (params != null && params.containsKey("allowed")) {
                List<String> values = new ArrayList<>();
                schema.get("enum").forEach(value -> values.add(value.asString()));
                assertThat(params.get("allowed"))
                        .isEqualTo(values.stream().sorted().toList());
            }
        }
        return errors;
    }

    private JsonNode document() {
        try {
            HttpResponse<String> response = CLIENT.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            return JSON.readTree(response.body());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** {@code query} on the strict route, with the probe header once. */
    private Response get(String query, String id) {
        return send(query, List.of(InputProbeController.HEADER, id));
    }

    private Response send(String query, List<String> headers) {
        return send(InputProbeController.STRICT_PATH, query, headers);
    }

    /** {@code query} exactly as written, and each header name and value pair in {@code headers}, in order. */
    private Response send(String path, String query, List<String> headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path + query));
        for (int i = 0; i < headers.size(); i += 2) {
            request.header(headers.get(i), headers.get(i + 1));
        }
        try {
            HttpResponse<String> response = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
