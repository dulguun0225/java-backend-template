package com.example.starterfixtures.layering.platform;

import com.example.starterfixtures.layering.greeting.GreetingService;

/** The platform tier calls a feature: the one violation {@code platformDependsOnNoFeature} must report. */
public final class PlatformCallsFeature {
    private PlatformCallsFeature() {}

    public static String call(GreetingService greeting) {
        return greeting.greet();
    }
}
