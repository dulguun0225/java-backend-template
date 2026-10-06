package com.example.starterfixtures.layering.billing;

import com.example.starterfixtures.layering.greeting.api.GreetingApi;
import com.example.starterfixtures.layering.platform.PlatformReadsGeneratedTree;

/** Calls another feature through its {@code api} package over an allowed edge, and the platform tier: not reported. */
final class BillingCallsGreeting {
    String call() {
        return GreetingApi.greet() + PlatformReadsGeneratedTree.name();
    }
}
