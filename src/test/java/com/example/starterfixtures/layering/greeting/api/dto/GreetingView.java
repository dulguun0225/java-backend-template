package com.example.starterfixtures.layering.greeting.api.dto;

/**
 * A class in a subpackage of feature {@code greeting}'s {@code api} package. Only the {@code api} package itself is a
 * feature's surface, so another feature's reference here is reported.
 */
public final class GreetingView {
    private GreetingView() {}

    /** A method, not a constant: javac inlines a constant, which would leave no dependency in the bytecode. */
    public static String text() {
        return "hello";
    }
}
