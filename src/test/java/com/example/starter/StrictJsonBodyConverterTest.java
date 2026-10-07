package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.example.starter.platform.DecimalNotStringException;
import com.example.starter.platform.StringDecimalDeserializer;
import com.example.starter.platform.error.BoundBody;
import com.example.starter.platform.error.FieldError;
import com.example.starter.platform.error.ValidationFailed;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
 * which code and params it carries ({@code allowed} the members declared, sorted), what {@code detail} says, that a
 * member given twice is refused at any depth with neither value bound, that a UUID is read in its 36-character form
 * only, that at most 100 entries are recorded and the rest counted, that the body limit holds with a declared length
 * and without one, and that nothing the caller sent is echoed. The mapper is
 * configured as Boot configures it — unknown properties not failing — so the reader is proven not to depend on
 * that setting.
 */
class StrictJsonBodyConverterTest {

    private static final String SENTINEL = "SENTINEL-7f3a";

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final StrictJsonBodyConverter converter = new StrictJsonBodyConverter(MAPPER, 65_536);

    /** The members {@link Sample} declares, sorted: what its undeclared members' refusals allow. */
    private static final List<String> SAMPLE_MEMBERS =
            List.of("big", "colour", "count", "flag", "inner", "labels", "name", "rate", "ref", "small", "tags");

    private static final List<String> INNER_MEMBERS = List.of("a");

    record Inner(@Nullable String a) {}

    enum Colour {
        RED,
        @JsonProperty("sky-blue")
        BLUE
    }

    record Sample(
            @Nullable String name,
            @Nullable Boolean flag,
            @Nullable Number count,

            @JsonDeserialize(using = StringDecimalDeserializer.class) @Nullable
            String rate,

            @Nullable Inner inner,
            @Nullable List<String> tags,
            @Nullable Map<String, String> labels,
            @Nullable UUID ref,
            @Nullable Integer small,
            @Nullable Long big,
            @Nullable Colour colour) {}

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

        assertThat(sample).isEqualTo(new Sample("X", true, 3, "2.5", null, List.of("t"), null, null, null, null, null));
    }

    @Test
    void everyUnknownMemberIsNamedAtItsPointerNestedOnesIncluded() {
        assertThat(failures("{\"name\":\"X\",\"foo\":1,\"bar\":{\"x\":[1]},\"inner\":{\"a\":\"b\",\"zz\":1}}"))
                .containsExactly(unknown("/bar"), unknown("/foo"), unknownInInner("/inner/zz"));
    }

    /**
     * A record's unknown members are replayed from a buffer after its closing brace; the pointer of one whose value
     * is an object or an array once came out wrong ({@code /foo/foo}, {@code /bar/1}).
     */
    @Test
    void anUnknownMemberHoldingAnObjectOrAnArrayIsNamedAtItsOwnPointer() {
        assertThat(failures("{\"foo\":{\"z\":1}}")).containsExactly(unknown("/foo"));
        assertThat(failures("{\"foo\":[1],\"bar\":2}")).containsExactly(unknown("/bar"), unknown("/foo"));
        assertThat(failures("{\"foo\":{\"x\":1},\"bar\":{\"y\":2},\"baz\":[[1]]}"))
                .containsExactly(unknown("/bar"), unknown("/baz"), unknown("/foo"));
        assertThat(failures("{\"inner\":{\"a\":\"x\",\"q\":[2],\"r\":{\"s\":1},\"t\":1},\"name\":\"n\"}"))
                .containsExactly(unknownInInner("/inner/q"), unknownInInner("/inner/r"), unknownInInner("/inner/t"));
    }

    @Test
    void aPathVariableSentAsAnObjectIsStillAnIdentifierInThePath() {
        pathVariables(Map.of("code", "ACC"));

        assertThat(failures("{\"code\":{\"x\":1},\"foo\":[1]}"))
                .containsExactly(
                        new FieldError(
                                "/code", "validation.identifier-in-path", new ApiFieldCode.IdentifierInPath(), null),
                        unknown("/foo"));
    }

    @Test
    void aPointerIsEscapedAsRfc6901Requires() {
        assertThat(failures("{\"a/b~c\":1}")).containsExactly(unknown("/a~1b~0c"));
    }

    @Test
    void anUndeclaredMemberGivenTwiceIsOneUnknownFieldAndADuplicate() {
        assertThat(failures("{\"foo\":1,\"foo\":2}")).containsExactly(duplicate("/foo"), unknown("/foo"));
    }

    /** Jackson binds the last of two equal keys; the reader refuses the body and binds neither. */
    @Test
    void aDeclaredMemberGivenTwiceIsRefusedAndNeitherValueIsBound() {
        AtomicBoolean ruled = new AtomicBoolean();
        BoundBody<Sample> body = read("{\"name\":\"first\",\"name\":\"second\"}");

        ValidationFailed failed = catchThrowableOfType(
                ValidationFailed.class,
                () -> body.validate((sample, errors) -> {
                    ruled.set(true);
                    return sample;
                }));

        assertThat(failed).isNotNull();
        assertThat(failed.errors()).containsExactly(duplicate("/name"));
        assertThat(ruled)
                .as("the field rules never see a body with a member given twice")
                .isFalse();
    }

    @Test
    void aMemberGivenThreeTimesIsNamedOnce() {
        assertThat(failures("{\"name\":\"a\",\"name\":\"b\",\"name\":\"c\"}")).containsExactly(duplicate("/name"));
    }

    @Test
    void aMemberGivenTwiceIsRefusedAtAnyDepthWhereItIs() {
        assertThat(failures("{\"inner\":{\"a\":\"x\",\"a\":\"y\"},\"labels\":{\"k\":\"1\",\"k\":\"2\"},"
                        + "\"foo\":[{\"z\":1},{\"z\":1,\"z\":2}],\"bar\":{\"a/b\":1,\"a/b\":2}}"))
                .containsExactly(
                        unknown("/bar"),
                        duplicate("/bar/a~1b"),
                        unknown("/foo"),
                        duplicate("/foo/1/z"),
                        duplicate("/inner/a"),
                        duplicate("/labels/k"));
    }

    @Test
    void aMemberGivenTwiceIsCollectedWithEveryOtherFailureOfTheBody() {
        assertThat(failures("{\"name\":\"a\",\"flag\":\"yes\",\"name\":\"b\",\"foo\":1,\"tags\":\"t\"}"))
                .containsExactly(
                        wrongType("/flag", "boolean"),
                        unknown("/foo"),
                        duplicate("/name"),
                        wrongType("/tags", "array"));
    }

    @Test
    void aMemberGivenOnceInEachOfTwoObjectsIsNoDuplicate() {
        assertThat(value(read("{\"inner\":{\"a\":\"x\"},\"labels\":{\"a\":\"y\"},\"name\":\"a\"}"))
                        .labels())
                .isEqualTo(Map.of("a", "y"));
    }

    @Test
    void aTopLevelMemberNamedAfterAPathVariableIsAnIdentifierInThePathWhateverItsValue() {
        pathVariables(Map.of("code", "ACC", "date", "2026-01-01"));

        assertThat(failures("{\"code\":\"ACC\",\"date\":null,\"inner\":{\"code\":1}}"))
                .containsExactly(
                        new FieldError(
                                "/code", "validation.identifier-in-path", new ApiFieldCode.IdentifierInPath(), null),
                        new FieldError(
                                "/date", "validation.identifier-in-path", new ApiFieldCode.IdentifierInPath(), null),
                        unknownInInner("/inner/code"));
    }

    @Test
    void aValueOfTheWrongJsonTypeIsNamedWithTheTypeExpected() {
        assertThat(failures("{\"flag\":\"yes\"}")).containsExactly(wrongType("/flag", "boolean"));
        assertThat(failures("{\"flag\":[true]}")).containsExactly(wrongType("/flag", "boolean"));
        assertThat(failures("{\"count\":\"abc\"}")).containsExactly(wrongType("/count", "number"));
        assertThat(failures("{\"count\":true}")).containsExactly(wrongType("/count", "number"));
        assertThat(failures("{\"small\":\"abc\"}")).containsExactly(wrongType("/small", "integer"));
        assertThat(failures("{\"ref\":5}")).containsExactly(wrongType("/ref", "string"));
        assertThat(failures("{\"ref\":{\"a\":1}}")).containsExactly(wrongType("/ref", "string"));
        assertThat(failures("{\"name\":{\"a\":1}}")).containsExactly(wrongType("/name", "string"));
        assertThat(failures("{\"name\":[1]}")).containsExactly(wrongType("/name", "string"));
        assertThat(failures("{\"tags\":\"t\"}")).containsExactly(wrongType("/tags", "array"));
        assertThat(failures("{\"inner\":\"x\"}")).containsExactly(wrongType("/inner", "object"));
    }

    /**
     * A UUID is RFC 9562's 36-character form, either case, untrimmed: Jackson's own reader takes a 24-character
     * base64 string, and {@code UUID.fromString} takes {@code 1-1-1-1-1}.
     */
    @Test
    void aUuidIsReadInItsThirtySixCharacterFormOnly() {
        UUID id = UUID.fromString("0190f0c4-7d2e-7b3a-9c4d-5e6f7a8b9c0d");
        assertThat(value(read("{\"ref\":\"" + id + "\"}")).ref()).isEqualTo(id);
        assertThat(value(read("{\"ref\":\"" + id.toString().toUpperCase(java.util.Locale.ROOT) + "\"}"))
                        .ref())
                .isEqualTo(id);
        for (String text : List.of(
                "1-1-1-1-1",
                "AZDwxH0uezqcTV5veouckA==",
                " " + id,
                id + " ",
                id.toString().replace("-", ""),
                "{" + id + "}",
                "urn:uuid:" + id,
                "")) {
            assertThat(failures("{\"ref\":\"" + text + "\"}")).as(text).containsExactly(invalidValue("/ref", "uuid"));
        }
    }

    /**
     * Jackson raises an integer past its type's range as a stream-read error, which ends the read: it names the member
     * and its format, and an undeclared member Jackson holds until the object closes is not named with it.
     */
    @Test
    void anIntegerPastItsRangeIsAnInvalidValueNamingItsFormat() {
        assertThat(failures("{\"small\":3000000000}")).containsExactly(invalidValue("/small", "int32"));
        assertThat(failures("{\"name\":\"n\",\"foo\":1,\"big\":99999999999999999999}"))
                .containsExactly(invalidValue("/big", "int64"));
        assertThat(value(read("{\"small\":2147483647,\"big\":-9223372036854775808}")))
                .extracting(Sample::small, Sample::big)
                .containsExactly(Integer.MAX_VALUE, Long.MIN_VALUE);
    }

    /** The values as the mapper writes them, a renamed constant included, sorted. */
    @Test
    void aValueOutsideItsEnumerationIsAnUnknownValueListingTheValues() {
        assertThat(failures("{\"colour\":\"PURPLE\"}"))
                .containsExactly(FieldError.of("/colour", new ApiFieldCode.UnknownValue(List.of("RED", "sky-blue"))));
        assertThat(value(read("{\"colour\":\"sky-blue\"}")).colour()).isEqualTo(Colour.BLUE);
    }

    /** One entry per undeclared member would let a body buy a response many times its size. */
    @Test
    void aPassRecordsAtMostOneHundredEntriesAndCountsTheRest() {
        String undeclared = IntStream.range(0, 101)
                .mapToObj(i -> "\"m" + (1000 + i) + "\":0")
                .collect(Collectors.joining(",", "{", "}"));
        ValidationFailed many = refused(undeclared);
        assertThat(many.errors())
                .hasSize(100)
                .allSatisfy(error -> assertThat(error.code()).isEqualTo("validation.unknown-field"));
        assertThat(many.errors().getFirst()).isEqualTo(unknown("/m1000"));
        assertThat(many.omitted()).isEqualTo(1);

        String repeated = IntStream.range(0, 101)
                .mapToObj(i -> "\"k" + (1000 + i) + "\":\"1\",\"k" + (1000 + i) + "\":\"2\"")
                .collect(Collectors.joining(",", "{\"labels\":{", "}}"));
        ValidationFailed twice = refused(repeated);
        assertThat(twice.errors())
                .hasSize(100)
                .allSatisfy(error -> assertThat(error.code()).isEqualTo("validation.duplicate-member"));
        assertThat(twice.omitted()).isEqualTo(1);

        String hundred = IntStream.range(0, 100)
                .mapToObj(i -> "\"m" + (1000 + i) + "\":0")
                .collect(Collectors.joining(",", "{", "}"));
        assertThat(refused(hundred).omitted()).isZero();
    }

    /**
     * A body of exactly the limit is read and one byte more is refused naming the limit, with a declared length and
     * without one; a declared length over the limit is refused before the body is read at all.
     */
    @Test
    void theBodyLimitHoldsWithADeclaredLengthAndWithoutOne() throws java.io.IOException {
        int limit = 64;
        StrictJsonBodyConverter limited = new StrictJsonBodyConverter(MAPPER, limit);
        String exactly = "{\"name\":\"" + "x".repeat(limit - 11) + "\"}";
        assertThat(exactly).hasSize(limit);
        for (boolean declared : List.of(true, false)) {
            assertThat(value(read(limited, message(exactly, declared))).name()).hasSize(limit - 11);
            RequestBodyTooLarge refused = catchThrowableOfType(
                    RequestBodyTooLarge.class, () -> read(limited, message(exactly + " ", declared)));
            assertThat(refused).as("declared length " + declared).isNotNull();
            assertThat(refused.max()).isEqualTo(limit);
        }
        MockHttpInputMessage unread = new MockHttpInputMessage(new InputStream() {
            @Override
            public int read() {
                throw new AssertionError("a body declared over the limit is never read");
            }
        });
        unread.getHeaders().setContentLength(limit + 1L);
        assertThat(catchThrowableOfType(RequestBodyTooLarge.class, () -> read(limited, unread)))
                .isNotNull();
        assertThat(catchThrowableOfType(IllegalArgumentException.class, () -> new StrictJsonBodyConverter(MAPPER, 0)))
                .isNotNull();
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
                "{\"rate\":{\"x\":\"" + SENTINEL + "\"}}",
                "{\"name\":\"" + SENTINEL + "\",\"name\":\"" + SENTINEL + "\"}")) {
            ValidationFailed failed = catchThrowableOfType(ValidationFailed.class, () -> value(read(json)));
            assertThat(failed).as(json).isNotNull();
            assertThat(failed.errors().toString()).as(json).doesNotContain(SENTINEL);
            assertThat(failed.getMessage()).as(json).doesNotContain(SENTINEL);
        }
    }

    private BoundBody<Sample> read(String json) {
        return read(converter, new MockHttpInputMessage(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static BoundBody<Sample> read(StrictJsonBodyConverter reader, MockHttpInputMessage message) {
        try {
            Object read =
                    reader.read(ResolvableType.forClassWithGenerics(BoundBody.class, Sample.class), message, null);
            @SuppressWarnings("unchecked")
            BoundBody<Sample> body = (BoundBody<Sample>) read;
            return body;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static MockHttpInputMessage message(String json, boolean declaredLength) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        MockHttpInputMessage message = new MockHttpInputMessage(bytes);
        if (declaredLength) {
            message.getHeaders().setContentLength(bytes.length);
        }
        return message;
    }

    private ValidationFailed refused(String json) {
        ValidationFailed failed = catchThrowableOfType(ValidationFailed.class, () -> value(read(json)));
        assertThat(failed).as(json).isNotNull();
        return failed;
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
        return new FieldError(pointer, "validation.unknown-field", new ApiFieldCode.UnknownField(SAMPLE_MEMBERS), null);
    }

    private static FieldError unknownInInner(String pointer) {
        return new FieldError(pointer, "validation.unknown-field", new ApiFieldCode.UnknownField(INNER_MEMBERS), null);
    }

    private static FieldError invalidValue(String pointer, String expected) {
        return new FieldError(
                pointer, "validation.invalid-value", new ApiFieldCode.InvalidValue(expected), "expected " + expected);
    }

    private static FieldError duplicate(String pointer) {
        return new FieldError(pointer, "validation.duplicate-member", new ApiFieldCode.DuplicateMember(), null);
    }

    private static FieldError wrongType(String pointer, String expected) {
        return new FieldError(
                pointer, "validation.wrong-type", new ApiFieldCode.WrongType(expected), "expected " + expected);
    }
}
