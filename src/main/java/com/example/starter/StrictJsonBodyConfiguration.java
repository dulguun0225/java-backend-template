package com.example.starter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registers {@link StrictJsonBodyConverter} as a custom server converter, which places it ahead of Boot's
 * defaults, so a {@code BoundBody} request body is read by it and never by the default Jackson converter. It
 * takes Boot's own {@link JsonMapper}, so the modules and features every response is written with are the ones
 * every request is read with, and the request-body limit, {@code api.request-body.max-size}: 64KB (65,536 bytes)
 * unless the environment sets {@code API_REQUEST_BODY_MAX_SIZE}, a per-project parameter listed in
 * {@code docs/GATES.md}.
 */
@Configuration(proxyBeanMethods = false)
class StrictJsonBodyConfiguration {

    @Bean
    ServerHttpMessageConvertersCustomizer strictJsonBodyConverter(
            JsonMapper mapper, @Value("${api.request-body.max-size}") DataSize maxSize) {
        long bytes = maxSize.toBytes();
        if (bytes < 1 || bytes >= Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "api.request-body.max-size must be between 1 byte and 2 GB, was " + bytes + " bytes");
        }
        StrictJsonBodyConverter converter = new StrictJsonBodyConverter(mapper, (int) bytes);
        return builder -> builder.addCustomConverter(converter);
    }
}
