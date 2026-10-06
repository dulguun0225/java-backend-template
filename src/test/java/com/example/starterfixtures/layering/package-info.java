/**
 * Fixture tree for {@code LayeringArchTest}'s negative controls, shaped like the main code one level down: the
 * platform tier ({@code platform}), the generated jOOQ stand-in ({@code db}), and four feature packages.
 * {@code greeting} is the feature the others reach into; {@code billing}, {@code feedback} and
 * {@code partnerplatform} each depend on it, and the last two carry {@code db} and {@code platform} inside their
 * names, so a slice filter matching by substring would drop them. Directly in this package, as in the main base
 * package, sit classes that belong to no slice: {@code RootWiring}, which the platform tier must not call, and
 * {@code RootController}, a controller outside every feature. Test sources only, outside the main import and the
 * component-scanned tree.
 */
@org.jspecify.annotations.NullMarked
package com.example.starterfixtures.layering;
