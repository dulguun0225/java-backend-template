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
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
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
 *
 * <p>Every operation declares its 400, since any one can refuse a query parameter it does not declare, and every
 * operation that takes a body its 413, the body limit's refusal; an operation's own declaration of either is kept.
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

    /**
     * Declares on every operation the refusals the edge sends for any of them: 400 {@code validation.failed}, and
     * 413 {@code request.too-large} where the operation takes a body. An operation's own declaration is kept.
     */
    @Bean
    OpenApiCustomizer inputRefusalsAreDeclared() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths()
                    .values()
                    .forEach(path -> path.readOperations().forEach(operation -> {
                        ApiResponses responses = operation.getResponses();
                        if (responses == null) {
                            responses = new ApiResponses();
                            operation.setResponses(responses);
                        }
                        if (!responses.containsKey("400")) {
                            responses.addApiResponse(
                                    "400",
                                    problemResponse(
                                            "validation.failed: a query parameter not declared"
                                                    + " (validation.unknown-field) or given twice (validation.duplicate-member),"
                                                    + " a parameter absent (validation.required), not parsing"
                                                    + " (validation.invalid-value) or outside its values (validation.unknown-value)"));
                        }
                        if (operation.getRequestBody() != null && !responses.containsKey("413")) {
                            responses.addApiResponse(
                                    "413",
                                    problemResponse(
                                            "request.too-large: the body is over the limit, which params.max gives"
                                                    + " in bytes"));
                        }
                    }));
        };
    }

    private static ApiResponse problemResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content()
                        .addMediaType(
                                "application/problem+json",
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/Problem"))));
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

    /**
     * RFC 9457 with a machine {@code code} from a compile-checked catalog. An entry of {@code errors} names its input
     * one of two ways, never both: a body member by {@code pointer}, a parameter by {@code in} and {@code name}.
     */
    private static Schema<?> problemSchema() {
        ObjectSchema fieldError = new ObjectSchema();
        fieldError.addProperty(
                "pointer",
                new StringSchema()
                        .description("RFC 6901 JSON pointer to a request-body member, e.g. /name; \"\" for the whole"
                                + " body. Absent when in and name name the input"));
        StringSchema in = new StringSchema();
        List.of("header", "path", "query").forEach(in::addEnumItem);
        in.setDescription("Where a path variable, query parameter or header travels, as OpenAPI names it. With name;"
                + " absent when pointer names the input");
        fieldError.addProperty("in", in);
        fieldError.addProperty(
                "name",
                new StringSchema()
                        .description("The parameter's name as this document declares it, a header's included,"
                                + " whatever spelling was sent; for an undeclared query parameter, the name sent"));
        fieldError.addProperty(
                "code", new StringSchema().description("A FieldCode wire string, e.g. validation.required"));
        fieldError.addProperty(
                "detail",
                new StringSchema()
                        .description("Caller-safe text naming what was expected, e.g. `expected boolean` on"
                                + " validation.wrong-type; never the value sent. Absent when the code alone says it"));
        ObjectSchema params = new ObjectSchema();
        params.setAdditionalProperties(Boolean.TRUE);
        params.setDescription("What is allowed, by name: exactly the params the code declares in the error catalog,"
                + " e.g. {\"max\": 100} on validation.too-long, {\"expected\": \"uuid\"} on"
                + " validation.invalid-value, {\"allowed\": [\"name\"]} on validation.unknown-field. Absent when the"
                + " code declares none. Never the value sent");
        fieldError.addProperty("params", params);
        fieldError.setRequired(List.of("code"));
        fieldError.setOneOf(
                List.of(new Schema<>().required(List.of("pointer")), new Schema<>().required(List.of("in", "name"))));

        ArraySchema errors = new ArraySchema();
        errors.setItems(fieldError);
        errors.setDescription("Present only on validation.failed; at most 100 entries");

        ObjectSchema problemParams = new ObjectSchema();
        problemParams.setAdditionalProperties(Boolean.TRUE);
        problemParams.setDescription("What is allowed, by name, for a code that declares params in the error catalog:"
                + " {\"max\": 65536} on request.too-large, the body limit in bytes. Absent when the code declares"
                + " none");

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
        problem.addProperty("params", problemParams);
        problem.addProperty("errors", errors);
        problem.addProperty(
                "errorsOmitted",
                new IntegerSchema()
                        .format("int64")
                        .description("On validation.failed: the failures found past the 100th and not listed."
                                + " Absent when every one is listed"));
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
