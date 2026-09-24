package com.example.starterfixtures;

import com.example.starter.platform.error.BoundBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The negative control for {@code BanListArchTest.requestBodiesBindThroughBoundBody}: {@code create} binds its
 * body as the record itself, which Boot's lenient default converter would read, and must be reported;
 * {@code update} binds its own record as {@code BoundBody} and must not be.
 */
@RestController
@RequestMapping("/fixture/raw-body")
class RawRequestBodyFixture {

    record CreateBody(String name) {}

    record UpdateBody(String name) {}

    @PostMapping
    String create(@RequestBody CreateBody body) {
        return body.name();
    }

    @PutMapping
    String update(@RequestBody BoundBody<UpdateBody> body) {
        return body.validate((value, errors) -> value.name());
    }
}
