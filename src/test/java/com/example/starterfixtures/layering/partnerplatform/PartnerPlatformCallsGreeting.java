package com.example.starterfixtures.layering.partnerplatform;

import com.example.starterfixtures.layering.greeting.GreetingService;
import com.example.starterfixtures.layering.platform.PlatformReadsGeneratedTree;

/**
 * Calls another feature's internal class over an edge the fixture map allows: reported by
 * {@code featuresReachAnotherFeatureOnlyThroughItsApi} only. Calls the platform tier: not reported.
 */
final class PartnerPlatformCallsGreeting {
    String call(GreetingService greeting) {
        return greeting.greet() + PlatformReadsGeneratedTree.name();
    }
}
