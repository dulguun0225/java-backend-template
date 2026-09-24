package com.example.starter.platform;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Jackson deserializer enforcing "all decimals are strings on the wire": a decimal field accepts only a
 * JSON string token. A JSON number is rejected with {@link DecimalNotStringException}, which the API
 * exception handler maps to 400 {@code money.number-not-string}. This closes the two silent traps of a
 * numeric money field: a client sending minor units as an integer (a 100× error) and a lossy double
 * round-trip. Attach with {@code @JsonDeserialize(using = StringDecimalDeserializer.class)}.
 *
 * <p>Any other token — a boolean, an object, an array — is handed to
 * {@link DeserializationContext#handleUnexpectedToken(Class, JsonParser)}, which the strict body reader
 * records as {@code validation.wrong-type} (expected string) at the member's pointer and skips as a whole. It
 * is never read as text: {@code getValueAsString} on an object's opening token would leave the parser inside
 * that object, and its members would then be read as members of the enclosing one.
 */
public final class StringDecimalDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
            throw new DecimalNotStringException(String.valueOf(parser.currentName()));
        }
        if (token != JsonToken.VALUE_STRING) {
            return (String) context.handleUnexpectedToken(String.class, parser);
        }
        return parser.getString();
    }
}
