package com.example.starterfixtures.layering.greeting.api;

/** Feature {@code greeting}'s public surface: the one package another feature may call into. */
public final class GreetingApi {
    private GreetingApi() {}

    /** A method, not a constant: javac inlines a constant, which would leave no dependency in the bytecode. */
    public static String greet() {
        return "hello";
    }
}
