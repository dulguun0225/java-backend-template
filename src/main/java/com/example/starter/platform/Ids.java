package com.example.starter.platform;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.util.UUID;

/**
 * The single sanctioned app-side id producer: UUIDv7, backed by the maintained FasterXML
 * java-uuid-generator rather than a hand-rolled bit layout. {@code IdsTest} pins version 7 and the
 * IETF variant. The database default {@code uuidv7()} (PostgreSQL 18 native) is the backstop for ad-hoc
 * SQL. Never {@code gen_random_uuid()} or {@code UUID.randomUUID()}: a random key scatters the primary
 * key index, which is the failure mode v7 exists to avoid.
 */
public final class Ids {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private Ids() {}

    /** A fresh UUIDv7 (48-bit millisecond timestamp, version 7, IETF variant). */
    public static UUID newId() {
        return GENERATOR.generate();
    }
}
