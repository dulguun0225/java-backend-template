package com.example.starter;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The contract's identity. The version is the URL major ({@code v1}); one committed document per major,
 * {@code openapi/v1.json}, diffed by {@code OpenApiSnapshotIT}.
 *
 * <p>The shared RFC 9457 {@code Problem} schema and the strong {@code ETag} header are registered here once,
 * so every operation refers to one definition instead of repeating the error shape per response.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("starter").version("v1"))
                .components(
                        new Components().addSchemas("Problem", problemSchema()).addHeaders("ETag", etagHeader()));
    }

    /** RFC 9457 with a machine {@code code} from a compile-checked catalog. */
    private static Schema<?> problemSchema() {
        ObjectSchema fieldError = new ObjectSchema();
        fieldError.addProperty("pointer", new StringSchema().description("RFC 6901 JSON pointer, e.g. /name"));
        fieldError.addProperty(
                "code", new StringSchema().description("A FieldCode wire string, e.g. validation.required"));
        fieldError.setRequired(List.of("pointer", "code"));

        ArraySchema errors = new ArraySchema();
        errors.setItems(fieldError);
        errors.setDescription("Present only on validation.failed");

        ObjectSchema problem = new ObjectSchema();
        problem.addProperty("type", new StringSchema());
        problem.addProperty("title", new StringSchema());
        problem.addProperty("status", new IntegerSchema());
        problem.addProperty(
                "code", new StringSchema().description("Stable machine code from one of the *ErrorCode catalogs"));
        problem.addProperty("errors", errors);
        problem.addProperty("incidentId", new StringSchema().description("Present only on platform.internal (500)"));
        problem.setRequired(List.of("status", "code"));
        return problem;
    }

    /**
     * The strong validator a versioned resource returns; declared once so the first endpoint with a version
     * column refers to it rather than respelling it.
     */
    private static Header etagHeader() {
        return new Header()
                .description("Strong ETag, the quoted decimal of the row's version, e.g. \"3\". Never weak.")
                .schema(new StringSchema());
    }
}
