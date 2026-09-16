package com.example.starterfixtures;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PatchFixture {
    @PatchMapping("/fixture")
    String patch() {
        return "no";
    }
}
