/**
 * Fixture feature {@code partnerplatform}: the fixture map allows {@code partnerplatform -> greeting}, and it calls
 * {@code greeting}'s internal {@code GreetingService} and a class in {@code greeting.api.dto}, a subpackage of the
 * {@code api} package, each of which {@code featuresReachAnotherFeatureOnlyThroughItsApi} must report; its name
 * contains {@code platform}, so a filter matching module names by substring would drop it. Its dependency on the
 * platform tier is not reported.
 */
@org.jspecify.annotations.NullMarked
package com.example.starterfixtures.layering.partnerplatform;
