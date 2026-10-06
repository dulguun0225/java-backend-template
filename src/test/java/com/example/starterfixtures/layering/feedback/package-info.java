/**
 * Fixture feature {@code feedback}: depends on feature {@code greeting} through {@code greeting.api} with no line in
 * the fixture map, which {@code featureDependenciesAreInTheAllowedMap} must report; its name contains {@code db}, so
 * a filter matching module names by substring would drop it. Its dependency on the platform tier is not reported.
 */
@org.jspecify.annotations.NullMarked
package com.example.starterfixtures.layering.feedback;
