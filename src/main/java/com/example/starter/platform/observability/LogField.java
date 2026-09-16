package com.example.starter.platform.observability;

import java.util.UUID;

/**
 * A typed structured log field rendered as an ECS key-value, never interpolated into the message.
 *
 * <p>This type is the PII prevention: there is no {@code String}-value factory and no {@code Object}
 * overload, so only whitelisted scalar and id types can be logged. A customer, a name or an account number
 * is unloggable by construction at the call site, not scrubbed downstream.
 */
public final class LogField {

    private final String key;
    private final Object value;

    private LogField(String key, Object value) {
        this.key = key;
        this.value = value;
    }

    /** An entity id: the safe way to reference a record in a log. */
    public static LogField id(String key, UUID id) {
        return new LogField(key, id);
    }

    /** A count or size. */
    public static LogField count(String key, long n) {
        return new LogField(key, n);
    }

    /** A boolean flag. */
    public static LogField flag(String key, boolean b) {
        return new LogField(key, b);
    }

    /** An enum code, by its {@code name()}, never free text. */
    public static LogField code(String key, Enum<?> e) {
        return new LogField(key, e.name());
    }

    String key() {
        return key;
    }

    Object value() {
        return value;
    }
}
