/**
 * Fixture feature {@code greeting}, the one the other fixture features reach into: {@code api} holds its public
 * surface, and {@code GreetingService} beside it and {@code api.dto} beneath it are internal. It depends on the
 * platform tier and the generated tree only, so no feature rule reports a class in it; the fixture map's line
 * {@code greeting -> billing}, which it does not take, is reported.
 */
@org.jspecify.annotations.NullMarked
package com.example.starterfixtures.layering.greeting;
