package com.example.starterfixtures.layering;

import com.example.starterfixtures.layering.greeting.GreetingService;

/**
 * A class directly in the base package calling a feature, as the application class or the HTTP edge might: the one
 * violation {@code basePackageDependsOnNoFeature} must report.
 */
final class RootCallsFeature {
    String call(GreetingService greeting) {
        return greeting.greet();
    }
}
