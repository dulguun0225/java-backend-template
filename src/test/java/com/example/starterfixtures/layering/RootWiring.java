package com.example.starterfixtures.layering;

/** Stands in for a class directly in the base package, such as the application class or the HTTP edge. */
public final class RootWiring {
    private RootWiring() {}

    public static String name() {
        return "wiring";
    }
}
