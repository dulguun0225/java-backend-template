/**
 * The cross-cutting RFC 9457 wire-error contract: {@link com.example.starter.platform.error.WireError}, the
 * compile-checked {@code (wire code, int status)} contract every feature's {@code *ErrorCode} enum
 * implements, plus the body records the render edges build from a catalog entry rather than an inline
 * string literal. The status is an {@code int} so this package stays free of the web tier.
 */
@org.jspecify.annotations.NullMarked
package com.example.starter.platform.error;
