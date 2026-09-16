package com.example.starter.platform.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.ArrayList;
import java.util.List;

/**
 * A test appender that snapshots each event's MDC at append time. Logback reads the MDC lazily on first access,
 * and the facade clears its scope right after emitting, so a plain ListAppender sees an empty map.
 */
final class CapturingAppender extends AppenderBase<ILoggingEvent> {

    final List<ILoggingEvent> events = new ArrayList<>();

    @Override
    protected void append(ILoggingEvent event) {
        event.prepareForDeferredProcessing();
        events.add(event);
    }
}
