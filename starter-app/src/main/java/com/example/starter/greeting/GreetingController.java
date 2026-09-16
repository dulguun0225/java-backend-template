package com.example.starter.greeting;

import com.example.starter.platform.error.ProblemBody;
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
 * The HTTP edge of the greeting feature. Business outcomes are mapped here to RFC 9457 problems whose
 * {@code code} comes from {@link GreetingErrorCode}; the framework edge (malformed body, validation) is
 * {@code ApiExceptionHandler}'s. Constructor injection only.
 */
@RestController
@RequestMapping("/api/greetings")
class GreetingController {

    private final GreetingService greetings;

    GreetingController(GreetingService greetings) {
        this.greetings = greetings;
    }

    @PostMapping
    ResponseEntity<GreetingView> create(@RequestBody CreateGreetingRequest request) {
        GreetingView created = greetings.create(request);
        return ResponseEntity.created(URI.create("/api/greetings/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    ResponseEntity<?> get(@PathVariable UUID id) {
        return greetings
                .find(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(GreetingErrorCode.NOT_FOUND.status())
                        .body(ProblemBody.of(GreetingErrorCode.NOT_FOUND)));
    }
}
