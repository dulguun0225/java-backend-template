/**
 * The platform tier: the cross-cutting foundation every feature builds on and that no feature owns —
 * {@code Money} and its rounding policy, the UUIDv7 id producer, the {@code Tx} database seam, the
 * RFC 9457 error contract ({@code platform.error}) and the typed logging facade
 * ({@code platform.observability}). It never depends on a feature package; features depend on it.
 */
@org.jspecify.annotations.NullMarked
package com.example.starter.platform;
