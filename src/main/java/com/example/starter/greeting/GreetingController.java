package com.example.starter.greeting;

import com.example.starter.platform.error.BoundBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The HTTP edge of the greeting feature. A business outcome leaves the service as a
 * {@code Rejected} carrying a {@link GreetingErrorCode}, and {@code ApiExceptionHandler} renders it as the
 * RFC 9457 problem the {@code @ApiResponses} below declare. Constructor injection only.
 *
 * <p>Every request body binds as {@code BoundBody<T>}, where {@code T} is a record this one operation owns: the
 * strict reader refuses a member {@code T} does not declare and a member named after a path variable, and the
 * service reaches the value only through {@code BoundBody.validate}, before its transaction. An update, when one
 * is added, is {@code PUT /api/greetings/{id}} binding its own {@code UpdateGreetingRequest}, which declares the
 * fields that operation writes and never {@code id}.
 */
@RestController
@RequestMapping("/api/greetings")
class GreetingController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final GreetingService greetings;

    GreetingController(GreetingService greetings) {
        this.greetings = greetings;
    }

    @PostMapping
    @Operation(operationId = "createGreeting", summary = "Create one greeting")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Created. `Location` is the new greeting's URL."),
        @ApiResponse(
                responseCode = "400",
                description = "validation.failed (a field rule, an undeclared member, a wrong JSON type),"
                        + " validation.malformed-body",
                content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = "#/components/schemas/Problem")))
    })
    ResponseEntity<GreetingView> createGreeting(@RequestBody BoundBody<CreateGreetingRequest> body) {
        GreetingView created = greetings.create(body);
        return ResponseEntity.created(URI.create("/api/greetings/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getGreeting", summary = "One greeting by its opaque id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(
                responseCode = "404",
                description = "not-found",
                content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(ref = "#/components/schemas/Problem")))
    })
    ResponseEntity<GreetingView> get(@PathVariable UUID id) {
        return ResponseEntity.ok(greetings.get(id));
    }
}
