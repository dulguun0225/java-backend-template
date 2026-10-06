package com.example.starterfixtures.layering.feedback;

import com.example.starterfixtures.layering.greeting.api.GreetingApi;
import com.example.starterfixtures.layering.platform.PlatformReadsGeneratedTree;

/**
 * Calls another feature through its {@code api} package with no line in the fixture map: reported by
 * {@code featureDependenciesAreInTheAllowedMap} only. Calls the platform tier: not reported.
 */
final class FeedbackCallsGreeting {
    String call() {
        return GreetingApi.greet() + PlatformReadsGeneratedTree.name();
    }
}
