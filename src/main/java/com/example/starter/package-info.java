/**
 * The deployable: the Spring Boot entry point, the HTTP edge (correlation filter, RFC 9457 exception
 * handler and its error catalog) and one package per feature beneath. Feature packages depend on the
 * platform tier and never on each other's internals; {@code LayeringArchTest} pins that.
 */
@org.jspecify.annotations.NullMarked
package com.example.starter;
