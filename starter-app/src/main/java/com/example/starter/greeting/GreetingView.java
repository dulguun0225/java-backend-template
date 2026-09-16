package com.example.starter.greeting;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Wire response: the server-owned representation a client renders. */
public record GreetingView(UUID id, String name, String message, OffsetDateTime createdAt) {}
