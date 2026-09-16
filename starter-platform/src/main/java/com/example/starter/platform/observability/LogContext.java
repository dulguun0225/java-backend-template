package com.example.starter.platform.observability;

import org.jspecify.annotations.Nullable;

/**
 * Holds the contextual log fields not derivable from the {@link Log} instance: the request or job
 * {@code correlation_id} (thread-scoped) and the process-wide {@code role} and {@code instance_id}. They are
 * established by visible wrappers, the HTTP edge filter and {@code main}, and read by the facade at emit
 * time. A context holder only: {@link Log} is the one place that writes MDC.
 */
public final class LogContext {

    static final String MODULE = "module";
    static final String CORRELATION_ID = "correlation_id";
    static final String ROLE = "role";
    static final String INSTANCE_ID = "instance_id";

    private static final ThreadLocal<String> CORRELATION = new ThreadLocal<>();

    private static volatile @Nullable String role;
    private static volatile @Nullable String instanceId;

    private LogContext() {}

    /** Set the process role and instance id once at startup, before anything emits. */
    public static void initProcess(String roleName, String instance) {
        role = roleName;
        instanceId = instance;
    }

    /** Bind the correlation id for the current request or job. */
    public static void bindCorrelation(String correlationId) {
        CORRELATION.set(correlationId);
    }

    public static @Nullable String correlationId() {
        return CORRELATION.get();
    }

    public static void clearCorrelation() {
        CORRELATION.remove();
    }

    static @Nullable String role() {
        return role;
    }

    static @Nullable String instanceId() {
        return instanceId;
    }
}
