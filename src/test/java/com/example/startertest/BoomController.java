package com.example.startertest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Test-only endpoint that throws, for {@code ErrorLeakIT}, which imports it explicitly. Outside the component-scanned tree so it never enters the OpenAPI document or a non-test context. Lives in test sources; never ships. */
@RestController
public class BoomController {

    public static final String PATH = "/api/test/boom";
    public static final String UNCODED_PATH = "/api/test/boom/uncoded";
    public static final String SENTINEL = "SENTINEL-must-never-reach-the-wire-7f3a";

    @GetMapping(PATH)
    String boom() {
        throw new IllegalStateException(SENTINEL);
    }

    /** A client-error status no catalog code carries, as a feature could throw it: for {@code ApiErrorEdgeIT}. */
    @GetMapping(UNCODED_PATH)
    String uncoded() {
        throw new ResponseStatusException(HttpStatus.CONFLICT, SENTINEL);
    }
}
