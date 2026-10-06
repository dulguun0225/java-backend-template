/**
 * Fixture tree for {@code LayeringArchTest}'s negative controls, shaped like the main code one level down: the
 * platform tier ({@code platform}), the generated jOOQ stand-in ({@code db}), and six feature packages, read with the
 * fixture map {@code LayeringArchTest.FIXTURE_ALLOWED_FEATURE_DEPENDENCIES}. {@code greeting} is the feature the
 * others reach into, through its {@code api} package or past it: {@code billing} over an allowed edge through
 * {@code api}, reported by nothing; {@code feedback} through {@code api} with no map line; {@code partnerplatform}
 * past {@code api}, into its internals and into its subpackage {@code api.dto}, over an allowed edge. The last two
 * carry {@code db} and {@code platform} inside their names, so a filter matching module names by substring would drop
 * them. {@code orders} and {@code inventory} call each other through their {@code api} packages, both directions
 * allowed: a cycle. The fixture map also lists {@code greeting -> billing}, which no dependency takes, and
 * {@code ghost -> greeting}, which names no feature here. Directly in this package, as in the main
 * base package, sit classes that belong to no module: {@code RootWiring}, which the platform tier must not call,
 * {@code RootCallsFeature}, which calls a feature, and {@code RootController}, a controller outside every feature.
 * Test sources only, outside the main import and the component-scanned tree.
 */
@org.jspecify.annotations.NullMarked
package com.example.starterfixtures.layering;
