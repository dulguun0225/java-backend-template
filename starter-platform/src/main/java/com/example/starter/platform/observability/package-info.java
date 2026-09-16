/**
 * The one typed logging facade and the sole sanctioned home of {@code org.slf4j} (Logger and MDC),
 * {@code System.out}/{@code System.err} and {@code printStackTrace}: everywhere else those are banned by
 * {@code BanListArchTest.noRawLoggingOutsideObservabilityFacade}, so all logging flows through
 * {@link com.example.starter.platform.observability.Log}. The facade emits structured ECS JSON (Spring Boot
 * native) and binds the mandatory MDC fields at emit time from their single sources. WARN and above name
 * a {@link com.example.starter.platform.observability.LogEvent} catalog constant, so the alert and grep
 * target is API rather than a free-form string; structured values are typed
 * {@link com.example.starter.platform.observability.LogField}s, so a domain object or a name is unloggable
 * by construction.
 */
@org.jspecify.annotations.NullMarked
package com.example.starter.platform.observability;
