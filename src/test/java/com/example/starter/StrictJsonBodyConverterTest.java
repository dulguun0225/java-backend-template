package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.example.starter.platform.DecimalNotStringException;
import com.example.starter.platform.StringDecimalDeserializer;
import com.example.starter.platform.error.BoundBody;
import com.example.starter.platform.error.FieldError;
import com.example.starter.platform.error.ValidationFailed;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.json.JsonMapper;

/**
 * The strict body reader in isolation: which member each binding failure names (an RFC 6901 pointer, escaped),
 * which code it carries, what {@code detail} says, and that nothing the caller sent is echoed. The mapper is
 * configured as Boot configures it — unknown properties not failing — so the reader is proven not to depend on
 * that setting.
 */
class StrictJsonBodyConverterTest {

    private static final String SENTINEL = "SENTINEL-7f3a";

    private final StrictJsonBodyConverter converter = new StrictJsonBodyConverter(JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build());

    record Inner(@Nullable String a) {}

    record Sample(
            @Nullable String name,
            @Nullable Boolean flag,
            @Nullable Number count,

            @JsonDeserialize(using = StringDecimalDeserializer.class) @Nullable
            String rate,

            @Nullable Inner inner,
            @Nullable List<String> tags) {}

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void readsOnlyABoundBodyOfJsonAndWritesNothing() {
        ResolvableType bound = ResolvableType.forClassWithGenerics(BoundBody.class, Sample.class);
        assertThat(converter.canRead(bound, MediaType.APPLICATION_JSON)).isTrue();
        assertThat(converter.canRead(bound, MediaType.parseMediaType("application/problem+json")))
                .isTrue();
        assertThat(converter.canRead(bound, MediaType.TEXT_PLAIN)).isFalse();
        assertThat(converter.canRead(ResolvableType.forClass(Sample.class), MediaType.APPLICATION_JSON))
                .isFalse();
        assertThat(converter.canWrite(bound, BoundBody.class, MediaType.APPLICATION_JSON))
                .isFalse();
    }

    @Test
    void aCleanBodyBindsWithNoFailure() {
        Sample sample = value(read("{\"name\":\"X\",\"flag\":true,\"count\":3,\"rate\":\"2.5\",\"tags\":[\"t\"]}"));

        assertThat(sample).isEqualTo(new Sample("X", true, 3, "2.5", null, List.of("t")));
    }

    @Test
    void everyUnknownMemberIsNamedAtItsPointerNestedOnesIncluded() {
        assertThat(failures("{\"name\":\"X\",\"foo\":1,\"bar\":{\"x\":[1]},\"inner\":{\"a\":\"b\",\"zz\":1}}"))
                .containsExactly(unknown("/bar"), unknown("/foo"), unknown("/inner/zz"));
    }

    @Test
    void aPointerIsEscapedAsRfc6901Requires() {
        assertThat(failures("{\"a/b~c\":1}")).containsExactly(unknown("/a~1b~0c"));
    }

    @Test
    void aMemberRepeatedIsNamedOnce() {
        assertThat(failures("{\"foo\":1,\"foo\":2}")).containsExactly(unknown("/foo"));
    }

    @Test
    void aTopLevelMemberNamedAfterAPathVariableIsAnIdentifierInThePathWhateverItsValue() {
        pathVariables(Map.of("code", "ACC", "date", "2026-01-01"));

        assertThat(failures("{\"code\":\"ACC\",\"date\":null,\"inner\":{\"code\":1}}"))
                .containsExactly(
                        new FieldError("/code", "validation.identifier-in-path"),
                        new FieldError("/date", "validation.identifier-in-path"),
                        unknown("/inner/code"));
    }

    @Test
    void aValueOfTheWrongJsonTypeIsNamedWithTheTypeExpected() {
        assertThat(failures("{\"flag\":\"yes\"}")).containsExactly(wrongType("/flag", "boolean"));
        assertThat(failures("{\"flag\":[true]}")).containsExactly(wrongType("/flag", "boolean"));
        assertThat(failures("{\"count\":\"abc\"}")).containsExactly(wrongType("/count", "integer"));
        assertThat(failures("{\"count\":true}")).containsExactly(wrongType("/count", "integer"));
        assertThat(failures("{\"name\":{\"a\":1}}")).containsExactly(wrongType("/name", "string"));
        assertThat(failures("{\"name\":[1]}")).containsExactly(wrongType("/name", "string"));
        assertThat(failures("{\"tags\":\"t\"}")).containsExactly(wrongType("/tags", "array"));
        assertThat(failures("{\"inner\":\"x\"}")).containsExactly(wrongType("/inner", "object"));
    }

    @Test
    void aFractionWhereAnIntegerIsExpectedBindsAsTheNumberItIsForTheRulesToJudge() {
        assertThat(value(read("{\"count\":1.5}")).count()).isEqualTo(1.5);
    }

    @Test
    void everyFailureOfOneBodyIsCollectedInOnePass() {
        assertThat(failures("{\"flag\":\"yes\",\"foo\":1,\"name\":{},\"bar\":2}"))
                .containsExactly(
                        unknown("/bar"), wrongType("/flag", "boolean"), unknown("/foo"), wrongType("/name", "string"));
    }

    /** The old reader read an object's members as the enclosing object's; they are now skipped with it. */
    @Test
    void anObjectWhereAStringDecimalIsExpectedIsOneWrongTypeAndLeaksNoMemberOutward() {
        assertThat(failures("{\"rate\":{\"name\":\"smuggled\",\"foo\":2},\"bar\":3}"))
                .containsExactly(unknown("/bar"), wrongType("/rate", "string"));
    }

    @Test
    void aJsonNumberWhereAStringDecimalIsExpectedIsStillMoneyNumberNotString() {
        HttpMessageNotReadableException refused = unreadable("{\"rate\":1}");

        assertThat(causes(refused)).anyMatch(DecimalNotStringException.class::isInstance);
    }

    @Test
    void inputThatIsNotAJsonObjectIsUnreadableWithAPositionAndNoValue() {
        assertThat(detail("{\"name\":\"" + SENTINEL + "\","))
                .isEqualTo("The request body is not well-formed JSON at line 1, column 25.");
        assertThat(detail("{\"name\":\"X\"\n,\n \"count\": 1e}"))
                .isEqualTo("The request body is not well-formed JSON at line 3, column 13.");
        assertThat(detail("{\"name\":\"X\"} {}")).startsWith("The request body is not well-formed JSON at line 1,");
        assertThat(detail("{\"name\":\"X\"} " + SENTINEL))
                .startsWith("The request body is not well-formed JSON at line 1,")
                .doesNotContain(SENTINEL);
        assertThat(detail("[1]")).isEqualTo("The request body must be a JSON object.");
        assertThat(detail("\"" + SENTINEL + "\"")).isEqualTo("The request body must be a JSON object.");
        assertThat(detail("null")).isEqualTo("The request body must be a JSON object.");
        assertThat(detail("   ")).isEqualTo("The request body is missing.");
    }

    @Test
    void noFailureEchoesTheValueSent() {
        for (String json : List.of(
                "{\"flag\":\"" + SENTINEL + "\"}",
                "{\"name\":[\"" + SENTINEL + "\"]}",
                "{\"foo\":\"" + SENTINEL + "\"}",
                "{\"rate\":{\"x\":\"" + SENTINEL + "\"}}")) {
            ValidationFailed failed = catchThrowableOfType(ValidationFailed.class, () -> value(read(json)));
            assertThat(failed).as(json).isNotNull();
            assertThat(failed.errors().toString()).as(json).doesNotContain(SENTINEL);
            assertThat(failed.getMessage()).as(json).doesNotContain(SENTINEL);
        }
    }

    private BoundBody<Sample> read(String json) {
        try {
            Object read = converter.read(
                    ResolvableType.forClassWithGenerics(BoundBody.class, Sample.class),
                    new MockHttpInputMessage(json.getBytes(StandardCharsets.UTF_8)),
                    null);
            @SuppressWarnings("unchecked")
            BoundBody<Sample> body = (BoundBody<Sample>) read;
            return body;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static Sample value(BoundBody<Sample> body) {
        return body.validate((sample, errors) -> sample);
    }

    private List<FieldError> failures(String json) {
        ValidationFailed failed = catchThrowableOfType(ValidationFailed.class, () -> value(read(json)));
        assertThat(failed).as(json).isNotNull();
        return failed.errors();
    }

    private HttpMessageNotReadableException unreadable(String json) {
        HttpMessageNotReadableException refused =
                catchThrowableOfType(HttpMessageNotReadableException.class, () -> read(json));
        assertThat(refused).as(json).isNotNull();
        return refused;
    }

    private String detail(String json) {
        UnreadableBody cause = UnreadableBody.in(unreadable(json));
        assertThat(cause).as(json).isNotNull();
        return Objects.requireNonNull(cause).detail();
    }

    private static List<Throwable> causes(Throwable ex) {
        List<Throwable> chain = new java.util.ArrayList<>();
        for (Throwable cause = ex; cause != null && chain.size() < 32; cause = cause.getCause()) {
            chain.add(cause);
        }
        return chain;
    }

    private static void pathVariables(Map<String, String> variables) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, variables);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static FieldError unknown(String pointer) {
        return new FieldError(pointer, "validation.unknown-field");
    }

    private static FieldError wrongType(String pointer, String expected) {
        return new FieldError(pointer, "validation.wrong-type", "expected " + expected);
    }
}
