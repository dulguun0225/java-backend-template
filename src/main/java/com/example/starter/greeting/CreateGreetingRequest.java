package com.example.starter.greeting;

import org.jspecify.annotations.Nullable;

/** Wire request: pre-validation, so the field is nullable and the service decides what is missing. */
public record CreateGreetingRequest(@Nullable String name) {}
