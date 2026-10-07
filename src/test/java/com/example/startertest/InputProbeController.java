package com.example.startertest;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints for {@code RequestInputEndpointIT}, which imports this class explicitly. Outside the
 * component-scanned tree, so they never enter the committed OpenAPI document or a non-test context; test sources
 * only.
 *
 * <p>{@code strict} declares a required {@code int32} query parameter, an optional enumeration query parameter and
 * a required UUID header, so each refusal of an input outside the body has an instance: absent, blank, not parsing,
 * outside its set, repeated, undeclared. {@code lenient} declares the same and also takes the raw request, which
 * may read any query parameter by hand, so the undeclared-query check does not reach it: the negative control,
 * showing that the same undeclared query parameter answers 200 where the check is not. {@code converted} declares
 * types whose text the check leaves to Spring's own conversion, a required {@code boolean} and an optional date, so
 * the edge's answer to a conversion Spring refuses, or turns into no value, has an instance too.
 */
@RestController
public class InputProbeController {

    public static final String STRICT_PATH = "/api/test/inputs";
    public static final String LENIENT_PATH = "/api/test/inputs/lenient";
    public static final String CONVERTED_PATH = "/api/test/inputs/converted";
    public static final String HEADER = "X-Probe-Id";

    public enum Colour {
        RED,
        GREEN,
        BLUE
    }

    @GetMapping(STRICT_PATH)
    String strict(
            @RequestParam Integer count,
            @RequestParam(required = false) @Nullable Colour colour,
            @RequestHeader(HEADER) UUID probeId) {
        return count + " " + colour + " " + probeId;
    }

    @GetMapping(CONVERTED_PATH)
    String converted(@RequestParam Boolean flag, @RequestParam(required = false) @Nullable LocalDate on) {
        return flag + " " + on;
    }

    @GetMapping(LENIENT_PATH)
    String lenient(@RequestParam Integer count, @RequestHeader(HEADER) UUID probeId, HttpServletRequest request) {
        return count + " " + probeId + " " + request.getMethod();
    }
}
