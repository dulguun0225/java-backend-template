package com.example.starterfixtures.ownership.greeting;

import static com.example.starter.db.Tables.GREETING;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jooq.DSLContext;

/** The owning feature writes its own table: not reported. */
class OwnerWritesItsTable {
    void insert(DSLContext dsl, UUID id, String name, OffsetDateTime now) {
        dsl.insertInto(GREETING)
                .set(GREETING.ID, id)
                .set(GREETING.NAME, name)
                .set(GREETING.CREATED_AT, now)
                .set(GREETING.VERSION, 1)
                .execute();
    }
}
