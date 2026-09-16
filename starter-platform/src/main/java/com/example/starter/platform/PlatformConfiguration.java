package com.example.starter.platform;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration root for the platform tier. The injectable {@link Clock} is the only sanctioned source
 * of wall-clock time: {@code Instant.now()} and friends are banned in main code by
 * {@code BanListArchTest.noWallClockReadsInMainCode}, so a test can pin the clock and a business date
 * never silently comes from the machine the code happens to run on. The clock ticks in microseconds, the
 * precision of PostgreSQL {@code timestamptz}, so a value written and read back compares equal.
 */
@Configuration(proxyBeanMethods = false)
class PlatformConfiguration {

    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.of(1, ChronoUnit.MICROS));
    }
}
