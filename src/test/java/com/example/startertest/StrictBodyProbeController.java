package com.example.startertest;

import com.example.starter.platform.Tx;
import com.example.starter.platform.error.BoundBody;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints for {@code StrictBodyEndpointIT}, which imports this class explicitly. Outside the
 * component-scanned tree, so they never enter the OpenAPI document or a non-test context; test sources only.
 *
 * <p>{@code update} is the shape an update takes: identifiers in the path, a body declaring only what the
 * operation writes. It keeps the sweep's path-identifier case non-vacuous while no main operation takes both a
 * body and a path variable. {@code lenient} binds its body raw, the spelling the ban list refuses in main code,
 * so the default converter reads it: the sweep's own negative control, showing the refusal it asserts is the
 * strict reader's and not the framework's. It also opens one read transaction, so the sweep's transaction count
 * is shown to count.
 */
@RestController
public class StrictBodyProbeController {

    public static final String UPDATE_PATH = "/api/test/strict-body/{code}/items/{itemNumber}";
    public static final String LENIENT_PATH = "/api/test/lenient-body";

    public record UpdateProbeRequest(
            @Nullable String name, @Nullable Boolean active) {}

    public record LenientProbeRequest(@Nullable String name) {}

    private final Tx tx;

    public StrictBodyProbeController(Tx tx) {
        this.tx = tx;
    }

    @PutMapping(UPDATE_PATH)
    String update(
            @PathVariable String code,
            @PathVariable String itemNumber,
            @RequestBody BoundBody<UpdateProbeRequest> body) {
        return code + itemNumber + body.validate((request, errors) -> String.valueOf(request.name()));
    }

    @PostMapping(LENIENT_PATH)
    String lenient(@RequestBody LenientProbeRequest body) {
        int rows = tx.read(dsl -> dsl.selectOne().fetch().size());
        return body.name() + rows;
    }
}
