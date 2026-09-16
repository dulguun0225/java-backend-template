package com.example.starterfixtures;

import java.time.Instant;

class WallClockFixture {
    Instant now() {
        return Instant.now();
    }
}
