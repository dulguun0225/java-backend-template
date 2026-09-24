package com.example.starterfixtures;

import com.example.starter.platform.StringDecimalDeserializer;
import com.example.starter.platform.error.BoundBody;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * The negative control for every check in {@code RequestBodyContractTest}: each handler here breaks exactly one
 * of them, and {@code RequestBodyContractTest.eachCheckReportsExactlyTheFixtureHandlersThatBreakIt} asserts
 * which. Every body binds as {@code BoundBody}, so the ban-list rule on request bodies stays silent here.
 */
@RestController
@RequestMapping("/fixture/contract/{tenant}")
class RequestBodyContractFixture {

    /** Carries {@code code}, the method's path variable, and {@code tenant}, the class's. */
    record EchoesPathVariables(String code, String tenant, String name) {}

    /** Not a record: a mutable bean Jackson would bind by setter. */
    static final class NotARecord {
        public String name = "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IgnoresUnknown(String name) {}

    record IgnoresMember(String name, @JsonIgnore String hidden) {}

    record CollectsTheRest(String name) {
        @JsonAnySetter
        void rest(String member, Object value) {}
    }

    /** {@code count}, {@code amount} and {@code tags} are refused; {@code rate}, {@code flag}, {@code size} pass. */
    record WrongMemberTypes(
            Integer count,
            BigDecimal amount,
            List<String> tags,

            @JsonDeserialize(using = StringDecimalDeserializer.class)
            String rate,

            Boolean flag,
            Number size) {}

    record SharedBody(String name) {}

    @PutMapping("/echo/{code}")
    String echo(@PathVariable String tenant, @PathVariable String code, @RequestBody BoundBody<EchoesPathVariables> b) {
        return tenant + code + b.validate((value, errors) -> value.name());
    }

    @PostMapping("/not-a-record")
    String notARecord(@PathVariable String tenant, @RequestBody BoundBody<NotARecord> b) {
        return tenant + b.validate((value, errors) -> value.name);
    }

    @PostMapping("/ignores-unknown")
    String ignoresUnknown(@PathVariable String tenant, @RequestBody BoundBody<IgnoresUnknown> b) {
        return tenant + b.validate((value, errors) -> value.name());
    }

    @PostMapping("/ignores-member")
    String ignoresMember(@PathVariable String tenant, @RequestBody BoundBody<IgnoresMember> b) {
        return tenant + b.validate((value, errors) -> value.name());
    }

    @PostMapping("/collects-the-rest")
    String collectsTheRest(@PathVariable String tenant, @RequestBody BoundBody<CollectsTheRest> b) {
        return tenant + b.validate((value, errors) -> value.name());
    }

    @PostMapping("/wrong-member-types")
    String wrongMemberTypes(@PathVariable String tenant, @RequestBody BoundBody<WrongMemberTypes> b) {
        return tenant + b.validate((value, errors) -> value.rate());
    }

    @PostMapping("/shared-a")
    String sharedA(@PathVariable String tenant, @RequestBody BoundBody<SharedBody> b) {
        return tenant + b.validate((value, errors) -> value.name());
    }

    @PutMapping("/shared-b")
    String sharedB(@PathVariable String tenant, @RequestBody BoundBody<SharedBody> b) {
        return tenant + b.validate((value, errors) -> value.name());
    }
}
