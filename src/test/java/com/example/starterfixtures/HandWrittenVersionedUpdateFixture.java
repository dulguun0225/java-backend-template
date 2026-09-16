package com.example.starterfixtures;

import static com.example.starter.db.Tables.GREETING;

import java.util.UUID;
import org.jooq.DSLContext;

/** Renders an UPDATE on a version-columned table by hand, skipping the VersionedUpdate guard. */
class HandWrittenVersionedUpdateFixture {
    int bump(DSLContext dsl, UUID id) {
        return dsl.update(GREETING)
                .set(GREETING.VERSION, GREETING.VERSION.plus(1))
                .where(GREETING.ID.eq(id))
                .execute();
    }
}
