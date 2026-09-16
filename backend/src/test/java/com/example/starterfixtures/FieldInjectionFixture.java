package com.example.starterfixtures;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;

@SuppressWarnings("NullAway")
class FieldInjectionFixture {
    @Autowired
    Clock clock;
}
