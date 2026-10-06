package com.example.starterfixtures.layering.partnerplatform;

import com.example.starterfixtures.layering.greeting.api.dto.GreetingView;

/**
 * Calls a class in a subpackage of another feature's {@code api} package over an edge the fixture map allows:
 * reported by {@code featuresReachAnotherFeatureOnlyThroughItsApi} only, since only the {@code api} package itself
 * is a feature's surface.
 */
final class PartnerPlatformCallsGreetingApiSubpackage {
    String call() {
        return GreetingView.text();
    }
}
