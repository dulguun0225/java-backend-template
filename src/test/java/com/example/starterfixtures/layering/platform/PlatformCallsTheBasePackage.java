package com.example.starterfixtures.layering.platform;

import com.example.starterfixtures.layering.RootWiring;

/** The platform tier calls a class directly in the base package: reported by {@code platformDependsOnNoFeature}. */
public final class PlatformCallsTheBasePackage {
    private PlatformCallsTheBasePackage() {}

    public static String call() {
        return RootWiring.name();
    }
}
