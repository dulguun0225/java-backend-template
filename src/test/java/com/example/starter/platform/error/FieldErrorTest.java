package com.example.starter.platform.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * A field error takes its code from its params record and carries those params on the wire: by name under
 * {@code params}, left off when the code declares none, and never paired by hand with another code's record.
 */
class FieldErrorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    enum TestCode implements FieldCode {
        TOO_LONG("validation.too-long", TooLong.class),
        REQUIRED("validation.required", Required.class);

        private final String wire;
        private final Class<? extends FieldParams> paramsType;

        TestCode(String wire, Class<? extends FieldParams> paramsType) {
            this.wire = wire;
            this.paramsType = paramsType;
        }

        @Override
        public String wire() {
            return wire;
        }

        @Override
        public Class<? extends FieldParams> paramsType() {
            return paramsType;
        }
    }

    record TooLong(int max) implements FieldParams {
        @Override
        public FieldCode code() {
            return TestCode.TOO_LONG;
        }
    }

    record Required() implements FieldParams {
        @Override
        public FieldCode code() {
            return TestCode.REQUIRED;
        }
    }

    /** A record naming a code that declares another record: the pairing the constructor refuses. */
    record Stray() implements FieldParams {
        @Override
        public FieldCode code() {
            return TestCode.REQUIRED;
        }
    }

    @Test
    void theCodeComesFromTheParamsRecord() {
        assertThat(FieldError.of("/name", new TooLong(100)))
                .isEqualTo(new FieldError("/name", "validation.too-long", new TooLong(100), null));
    }

    @Test
    void theParamsTravelByNameAndOnlyWhenTheCodeDeclaresSome() {
        assertThat(JSON.writeValueAsString(FieldError.of("/name", new TooLong(100))))
                .isEqualTo("{\"pointer\":\"/name\",\"code\":\"validation.too-long\",\"params\":{\"max\":100}}");
        assertThat(JSON.writeValueAsString(FieldError.of("/name", new Required())))
                .isEqualTo("{\"pointer\":\"/name\",\"code\":\"validation.required\"}");
        assertThat(JSON.writeValueAsString(FieldError.of("/name", new TooLong(3), "expected at most 3")))
                .isEqualTo("{\"pointer\":\"/name\",\"code\":\"validation.too-long\",\"params\":{\"max\":3},"
                        + "\"detail\":\"expected at most 3\"}");
    }

    /** A parameter is named by OpenAPI's location and name, a body member by its pointer, and never both. */
    @Test
    void aParameterIsNamedByItsLocationAndNameAndNeverAlsoByAPointer() {
        assertThat(JSON.writeValueAsString(
                        FieldError.ofParameter(FieldError.In.PATH, "id", new TooLong(3), "expected at most 3")))
                .isEqualTo("{\"in\":\"path\",\"name\":\"id\",\"code\":\"validation.too-long\","
                        + "\"params\":{\"max\":3},\"detail\":\"expected at most 3\"}");
        assertThat(JSON.writeValueAsString(FieldError.ofParameter(FieldError.In.HEADER, "X-Id", new Required())))
                .isEqualTo("{\"in\":\"header\",\"name\":\"X-Id\",\"code\":\"validation.required\"}");
        assertThat(FieldError.ofParameter(FieldError.In.QUERY, "q", new Required()))
                .isEqualTo(new FieldError(null, FieldError.In.QUERY, "q", "validation.required", new Required(), null));
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new FieldError("/q", FieldError.In.QUERY, "q", "validation.required", new Required(), null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FieldError(null, null, null, "validation.required", new Required(), null));
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new FieldError(null, FieldError.In.QUERY, null, "validation.required", new Required(), null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FieldError(null, null, "q", "validation.required", new Required(), null));
    }

    @Test
    void aCodeIsNeverPairedWithAnotherCodesParams() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FieldError("/name", "validation.too-long", new Required(), null));
        assertThatIllegalArgumentException().isThrownBy(() -> FieldError.of("/name", new Stray()));
    }
}
