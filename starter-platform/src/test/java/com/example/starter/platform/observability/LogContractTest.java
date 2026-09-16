package com.example.starter.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.example.starter.platform.Money;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * The facade's contract: the mandatory fields are bound from their single sources at emit time, the module is
 * derived from the package, structured values are key-values not message text, and emitting outside any request
 * degrades cleanly.
 */
class LogContractTest {

    @AfterEach
    void clear() {
        LogContext.clearCorrelation();
    }

    @Test
    void correlationModuleRoleAndInstanceAreBoundAtEmitTime() {
        CapturingAppender appender = capture(Money.class);
        LogContext.initProcess("api", "instance-1");
        LogContext.bindCorrelation("corr-42");

        Log.forClass(Money.class).info("hello", LogField.count("rows", 3), LogField.id("greeting_id", new UUID(0, 7)));

        Map<String, String> mdc = single(appender).getMDCPropertyMap();
        assertThat(mdc)
                .containsEntry("correlation_id", "corr-42")
                .containsEntry("module", "platform")
                .containsEntry("role", "api")
                .containsEntry("instance_id", "instance-1");
        assertThat(single(appender).getKeyValuePairs()).extracting(kv -> kv.key).contains("rows", "greeting_id");
        assertThat(single(appender).getFormattedMessage()).isEqualTo("hello");
    }

    @Test
    void moduleIsTheFirstPackageSegmentBelowTheBase() {
        assertThat(Log.moduleOf(Money.class)).isEqualTo("platform");
        assertThat(Log.moduleOf(LogContractTest.class)).isEqualTo("platform");
    }

    @Test
    void aCatalogEventLogsAtItsDeclaredLevelWithTheWireKeyAsMessage() {
        CapturingAppender appender = capture(Money.class);
        Log.forClass(Money.class).event(LogEvent.REQUEST_UNHANDLED_ERROR, new IllegalStateException("boom"));

        ILoggingEvent event = single(appender);
        assertThat(event.getMessage()).isEqualTo("request.unhandled-error");
        assertThat(event.getLevel().toString()).isEqualTo("ERROR");
        assertThat(event.getThrowableProxy()).isNotNull();
    }

    @Test
    void emitOutsideARequestDegradesCleanly() {
        CapturingAppender appender = capture(Money.class);
        Log log = Log.forClass(Money.class);
        assertThatCode(() -> log.info("startup")).doesNotThrowAnyException();
        assertThat(single(appender).getMDCPropertyMap()).doesNotContainKey("correlation_id");
    }

    private static CapturingAppender capture(Class<?> type) {
        Logger logger = (Logger) LoggerFactory.getLogger(type);
        logger.detachAndStopAllAppenders();
        CapturingAppender appender = new CapturingAppender();
        appender.start();
        logger.addAppender(appender);
        logger.setAdditive(false);
        return appender;
    }

    private static ILoggingEvent single(CapturingAppender appender) {
        assertThat(appender.events).hasSize(1);
        return appender.events.get(0);
    }
}
