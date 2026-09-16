package com.example.starterfixtures;

import reactor.core.publisher.Mono;

class ReactiveFixture {
    Mono<String> hello() {
        return new Mono<>();
    }
}
