/**
 * Violating fixtures for {@code BanListNegativeControlTest}: each class here breaks one ban on purpose so the rule
 * that bans it is proven to fire. The subpackages {@code ownership} and {@code layering} are fixture trees for
 * {@code TableOwnershipTest} and {@code LayeringArchTest}, shaped like the main code one level down. Test sources only, and outside the component-scanned tree so a fixture controller never enters a test context. Third-party types
 * that are not on the classpath are stubbed in {@code src/test/java} under their real package names.
 */
@org.jspecify.annotations.NullMarked
package com.example.starterfixtures;
