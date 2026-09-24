package com.example.starter;

import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registers {@link StrictJsonBodyConverter} as a custom server converter, which places it ahead of Boot's
 * defaults, so a {@code BoundBody} request body is read by it and never by the default Jackson converter. It
 * takes Boot's own {@link JsonMapper}, so the modules and features every response is written with are the ones
 * every request is read with.
 */
@Configuration(proxyBeanMethods = false)
class StrictJsonBodyConfiguration {

    @Bean
    ServerHttpMessageConvertersCustomizer strictJsonBodyConverter(JsonMapper mapper) {
        return builder -> builder.addCustomConverter(new StrictJsonBodyConverter(mapper));
    }
}
