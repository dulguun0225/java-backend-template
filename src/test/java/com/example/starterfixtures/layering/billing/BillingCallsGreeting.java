package com.example.starterfixtures.layering.billing;

import com.example.starterfixtures.layering.greeting.GreetingService;
import com.example.starterfixtures.layering.platform.PlatformReadsGeneratedTree;

/** Calls another feature's class: reported. Calls the platform tier: not reported. */
final class BillingCallsGreeting {
    String call(GreetingService greeting) {
        return greeting.greet() + PlatformReadsGeneratedTree.name();
    }
}
