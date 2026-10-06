/**
 * The deployable: the Spring Boot entry point, the HTTP edge (correlation filter, RFC 9457 exception
 * handler and its error catalog), none of which depends on a feature, and one package per feature beneath.
 * Feature packages depend on the platform tier; a feature reaches another only through that feature's
 * {@code api} package, over an edge listed in {@code LayeringArchTest.ALLOWED_FEATURE_DEPENDENCIES}.
 * {@code LayeringArchTest} pins that.
 */
@org.jspecify.annotations.NullMarked
package com.example.starter;
