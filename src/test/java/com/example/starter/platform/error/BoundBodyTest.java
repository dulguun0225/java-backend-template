package com.example.starter.platform.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * The one accessor of a bound request body: the rules see the value only through {@link BoundBody#validate},
 * and one {@link ValidationFailed} names every binding failure and every rule failure of the request together.
 */
class BoundBodyTest {

    private static final FieldError UNKNOWN_FOO = new FieldError("/foo", "validation.unknown-field");
    private static final FieldError UNKNOWN_BAR = new FieldError("/bar", "validation.unknown-field");
    private static final FieldError NAME_WRONG_TYPE =
            new FieldError("/name", "validation.wrong-type", "expected string");
    private static final FieldError NAME_REQUIRED = new FieldError("/name", "validation.required");
    private static final FieldError CODE_REQUIRED = new FieldError("/code", "validation.required");

    record Sample(String name) {}

    @Test
    void aCleanBodyReturnsWhatTheRulesProduce() {
        String result = BoundBody.of(new Sample("X"), List.of()).validate((sample, errors) -> sample.name() + "!");

        assertThat(result).isEqualTo("X!");
    }

    @Test
    void bindingFailuresComeFirstDeduplicatedAndSortedByPointerThenTheRuleFailures() {
        BoundBody<Sample> body = BoundBody.of(new Sample("X"), List.of(UNKNOWN_FOO, UNKNOWN_BAR, UNKNOWN_FOO));

        List<FieldError> errors = failures(() -> body.validate((sample, found) -> {
            found.add(CODE_REQUIRED);
            return null;
        }));

        assertThat(errors).containsExactly(UNKNOWN_BAR, UNKNOWN_FOO, CODE_REQUIRED);
    }

    /** A member of the wrong type binds as absent; the rule's "required" for it would name it twice. */
    @Test
    void aRuleFailureAtAPointerThatAlreadyHoldsABindingFailureIsDropped() {
        BoundBody<Sample> body = BoundBody.of(new Sample("X"), List.of(NAME_WRONG_TYPE));

        List<FieldError> errors = failures(() -> body.validate((sample, found) -> {
            found.add(NAME_REQUIRED);
            found.add(CODE_REQUIRED);
            return null;
        }));

        assertThat(errors).containsExactly(NAME_WRONG_TYPE, CODE_REQUIRED);
    }

    @Test
    void bindingFailuresAloneRefuseTheBodyEvenWhenTheRulesPass() {
        BoundBody<Sample> body = BoundBody.of(new Sample("X"), List.of(UNKNOWN_FOO));

        assertThat(failures(() -> body.validate((sample, found) -> sample))).containsExactly(UNKNOWN_FOO);
    }

    @Test
    void anUnboundBodyIsRefusedWithItsBindingFailuresAndTheRulesNeverRun() {
        AtomicBoolean ran = new AtomicBoolean();
        BoundBody<Sample> body = BoundBody.unbound(List.of(NAME_WRONG_TYPE));

        assertThat(failures(() -> body.validate((sample, found) -> {
                    ran.set(true);
                    return sample;
                })))
                .containsExactly(NAME_WRONG_TYPE);
        assertThat(ran).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> BoundBody.unbound(List.of()));
    }

    @Test
    void anAbsentOptionalBodyIsJudgedAsItsEmptyValue() {
        BoundBody<Sample> body = BoundBody.absent(new Sample("absent"));

        String name = body.validate((sample, found) -> sample.name());

        assertThat(name).isEqualTo("absent");
    }

    @Test
    void aRuleThatThrowsValidationFailedHasItsFailuresMergedTheSameWay() {
        BoundBody<Sample> body = BoundBody.of(new Sample("X"), List.of(NAME_WRONG_TYPE));

        List<FieldError> errors = failures(() -> body.validate((sample, found) -> {
            throw new ValidationFailed(List.of(NAME_REQUIRED, CODE_REQUIRED));
        }));

        assertThat(errors).containsExactly(NAME_WRONG_TYPE, CODE_REQUIRED);
    }

    @Test
    void rulesThatProduceNoValueAndReportNoFailureAreAProgrammingError() {
        BoundBody<Sample> body = BoundBody.of(new Sample("X"), List.of());

        assertThatIllegalStateException().isThrownBy(() -> body.validate((sample, found) -> null));
    }

    private static List<FieldError> failures(Runnable validation) {
        ValidationFailed failed = catchThrowableOfType(ValidationFailed.class, validation::run);
        assertThat(failed).as("the body was expected to be refused").isNotNull();
        return failed.errors();
    }
}
