package com.example.starter;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The contract's identity. The version is the URL major ({@code v1}); one committed document per major,
 * {@code openapi/v1.json}, diffed by {@code OpenApiSnapshotIT}.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info().title("starter").version("v1"));
    }
}
