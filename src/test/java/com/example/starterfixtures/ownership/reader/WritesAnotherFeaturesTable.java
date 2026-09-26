package com.example.starterfixtures.ownership.reader;

import static com.example.starter.db.Tables.GREETING;

import com.example.starter.platform.Tx;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A feature writes a table another feature owns, the way a service does: the one violation the rule must report. */
class WritesAnotherFeaturesTable {
    int insert(Tx tx, UUID id, String name, OffsetDateTime now) {
        return tx.write(dsl -> dsl.insertInto(GREETING)
                .set(GREETING.ID, id)
                .set(GREETING.NAME, name)
                .set(GREETING.CREATED_AT, now)
                .set(GREETING.VERSION, 1)
                .execute());
    }
}
