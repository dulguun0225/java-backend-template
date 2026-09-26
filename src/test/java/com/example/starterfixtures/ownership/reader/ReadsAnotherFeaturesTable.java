package com.example.starterfixtures.ownership.reader;

import static com.example.starter.db.Tables.GREETING;

import com.example.starter.db.tables.records.GreetingRecord;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;

/** A feature reads a table another feature owns: not reported. */
class ReadsAnotherFeaturesTable {
    List<GreetingRecord> byId(DSLContext dsl, UUID id) {
        return dsl.selectFrom(GREETING).where(GREETING.ID.eq(id)).fetch();
    }
}
