package com.example.starter.platform;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Money on the wire: {@code {"amount": "10000.00", "currency": "USD"}}, a string decimal at the currency's
 * minor-unit scale plus its ISO 4217 code. The shared component for every money field crossing the HTTP
 * boundary. {@code amount} is string-only: a JSON number is rejected by {@link StringDecimalDeserializer}.
 *
 * <p>Pre-validation, so both fields are nullable. The consuming service validates presence, currency and
 * scale, producing field-level {@code validation.failed} errors before mapping to {@link Money}. A wire
 * DTO only; it carries no arithmetic.
 */
public record MonetaryAmount(
        @JsonDeserialize(using = StringDecimalDeserializer.class) @Nullable
        String amount,

        @Nullable String currency) {}
