package com.example.starter;

import com.example.starter.platform.error.BoundBody;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The contract's identity. The version is the URL major ({@code v1}); one committed document per major,
 * {@code openapi/v1.json}, diffed by {@code OpenApiSnapshotIT}.
 *
 * <p>The shared RFC 9457 {@code Problem} schema and the strong {@code ETag} header are registered here once,
 * so every operation refers to one definition instead of repeating the error shape per response.
 *
 * <p>A request body binds as {@link BoundBody}{@code <T>}, and the document describes {@code T}: the wrapper is
 * how the strict reader hands its binding failures to the service, not part of the wire. Every request-body
 * schema, and every object schema it reaches, declares {@code additionalProperties: false}, which is what the
 * reader enforces: a member the schema does not declare is refused as {@code validation.unknown-field}. The
 * vacuum rule {@code request-body-schemas-are-closed} fails the committed document when one does not.
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

    /** Describes a {@code BoundBody<T>} request body as {@code T}. */
    @Bean
    ModelConverter boundBodyIsItsValue() {
        return new ModelConverter() {
            @Override
            public io.swagger.v3.oas.models.media.@Nullable Schema<?> resolve(
                    AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
                com.fasterxml.jackson.databind.JavaType javaType = Json.mapper().constructType(type.getType());
                if (javaType.getRawClass() == BoundBody.class && javaType.containedTypeCount() == 1) {
                    return context.resolve(new AnnotatedType(javaType.containedType(0))
                            .ctxAnnotations(type.getCtxAnnotations())
                            .jsonViewAnnotation(type.getJsonViewAnnotation())
                            .resolveAsRef(type.isResolveAsRef())
                            .schemaProperty(type.isSchemaProperty()));
                }
                return chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
            }
        };
    }

    /**
     * Every request-body schema is closed: {@code additionalProperties: false} on the body's own schema and on
     * every object schema it reaches through a property or an array's items, the rule the strict body reader
     * enforces at every depth, so the contract says what the service refuses.
     */
    @Bean
    OpenApiCustomizer requestBodiesAreClosed() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            @SuppressWarnings("rawtypes")
            Map<String, Schema> schemas = openApi.getComponents() == null
                    ? null
                    : openApi.getComponents().getSchemas();
            Set<String> closed = new HashSet<>();
            openApi.getPaths()
                    .values()
                    .forEach(path ->
                            path.readOperations().forEach(operation -> closeRequestBody(operation, schemas, closed)));
        };
    }

    @SuppressWarnings("rawtypes")
    private static void closeRequestBody(
            Operation operation, @Nullable Map<String, Schema> schemas, Set<String> closed) {
        RequestBody body = operation.getRequestBody();
        if (body == null || body.getContent() == null) {
            return;
        }
        for (MediaType media : body.getContent().values()) {
            Schema<?> schema = media.getSchema();
            if (schema != null) {
                close(schema, schemas, closed);
            }
        }
    }

    /** Closes one schema: follows a component {@code $ref} once, then every property and array item beneath. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void close(Schema<?> schema, @Nullable Map<String, Schema> schemas, Set<String> closed) {
        String ref = schema.get$ref();
        if (ref != null) {
            String name = ref.substring(ref.lastIndexOf('/') + 1);
            Schema<?> referenced = schemas == null ? null : schemas.get(name);
            if (referenced != null && closed.add(name)) {
                close(referenced, schemas, closed);
            }
            return;
        }
        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            schema.setAdditionalProperties(Boolean.FALSE);
            properties.values().forEach(property -> close(property, schemas, closed));
        }
        Schema<?> items = schema.getItems();
        if (items != null) {
            close(items, schemas, closed);
        }
    }

    /** RFC 9457 with a machine {@code code} from a compile-checked catalog. */
    private static Schema<?> problemSchema() {
        ObjectSchema fieldError = new ObjectSchema();
        fieldError.addProperty("pointer", new StringSchema().description("RFC 6901 JSON pointer, e.g. /name"));
        fieldError.addProperty(
                "code", new StringSchema().description("A FieldCode wire string, e.g. validation.required"));
        fieldError.addProperty(
                "detail",
                new StringSchema()
                        .description("Caller-safe text naming what was expected, e.g. `expected boolean` on"
                                + " validation.wrong-type; never the value sent. Absent when the code alone says it"));
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
        problem.addProperty(
                "detail",
                new StringSchema()
                        .description("Caller-safe text, never the value sent. On validation.malformed-body: the line"
                                + " and column where the JSON stopped being well formed, or that the body is missing"
                                + " or not an object"));
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
