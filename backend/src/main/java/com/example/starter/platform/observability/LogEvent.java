package com.example.starter.platform.observability;

import org.slf4j.event.Level;

/**
 * The compile-checked WARN-and-above event catalog: every warning or error a module emits names a constant
 * here, so alert rules and {@code grep} target a stable {@code wire} key rather than a free-form message.
 * The {@code wire} value is immutable once shipped. INFO and DEBUG are not catalog events; they carry a
 * static-literal message.
 */
public enum LogEvent {

    /** An unexpected throwable reached the API edge and became a coded 500. */
    REQUEST_UNHANDLED_ERROR("request.unhandled-error", Level.ERROR);

    private final String wire;
    private final Level level;

    LogEvent(String wire, Level level) {
        this.wire = wire;
        this.level = level;
    }

    /** The stable catalog key: the grep and alert target. */
    public String wire() {
        return wire;
    }

    Level level() {
        return level;
    }
}
