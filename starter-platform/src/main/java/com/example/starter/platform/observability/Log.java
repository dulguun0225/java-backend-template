package com.example.starter.platform.observability;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * The one typed logging facade. Obtain one per class with {@link #forClass(Class)}; the {@code module} field
 * is derived from the package. WARN and above name a {@link LogEvent} catalog constant; INFO and DEBUG
 * carry a static-literal message. Structured values are typed {@link LogField}s. At emit time the facade
 * binds the mandatory MDC fields from their single sources ({@code module} from this instance, the rest
 * from {@link LogContext}) so they are always consistent with the live context.
 *
 * <p>This is the sole sanctioned user of {@code org.slf4j} in main code.
 */
public final class Log {

    private static final String BASE = "com.example.starter";

    private final Logger delegate;
    private final String module;

    private Log(Logger delegate, String module) {
        this.delegate = delegate;
        this.module = module;
    }

    public static Log forClass(Class<?> type) {
        return new Log(LoggerFactory.getLogger(type), moduleOf(type));
    }

    /** {@code com.example.starter.<module>.…} becomes {@code <module>}; the root package becomes {@code app}. */
    static String moduleOf(Class<?> type) {
        String pkg = type.getPackageName();
        if (pkg.equals(BASE) || !pkg.startsWith(BASE + ".")) {
            return "app";
        }
        String rest = pkg.substring(BASE.length() + 1);
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    public void event(LogEvent event, LogField... fields) {
        emit(event.level(), event.wire(), null, fields);
    }

    /** A catalog event with the throwable that caused it; the stack trace is preserved, never swallowed. */
    public void event(LogEvent event, Throwable cause, LogField... fields) {
        emit(event.level(), event.wire(), cause, fields);
    }

    public void info(String message, LogField... fields) {
        emit(Level.INFO, message, null, fields);
    }

    public void debug(String message, LogField... fields) {
        emit(Level.DEBUG, message, null, fields);
    }

    private void emit(Level level, String message, @Nullable Throwable cause, LogField... fields) {
        try (Scope ignored = new Scope(module)) {
            LoggingEventBuilder builder = delegate.atLevel(level).setMessage(message);
            for (LogField field : fields) {
                builder = builder.addKeyValue(field.key(), field.value());
            }
            if (cause != null) {
                builder = builder.setCause(cause);
            }
            builder.log();
        }
    }

    /** Binds the mandatory MDC fields for one emit, then removes them. The facade owns every MDC write. */
    private static final class Scope implements AutoCloseable {

        private final List<MDC.MDCCloseable> handles = new ArrayList<>();

        Scope(String module) {
            put(LogContext.MODULE, module);
            put(LogContext.CORRELATION_ID, LogContext.correlationId());
            put(LogContext.ROLE, LogContext.role());
            put(LogContext.INSTANCE_ID, LogContext.instanceId());
        }

        private void put(String key, @Nullable String value) {
            if (value != null) {
                handles.add(MDC.putCloseable(key, value));
            }
        }

        @Override
        public void close() {
            for (int i = handles.size() - 1; i >= 0; i--) {
                handles.get(i).close();
            }
        }
    }
}
