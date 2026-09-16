package com.example.starterfixtures;

import com.example.starter.db.tables.records.GreetingRecord;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;

class SilentFetcherFixture {
    @Nullable
    GreetingRecord first(DSLContext dsl) {
        return dsl.selectFrom(com.example.starter.db.Tables.GREETING).fetchAny();
    }
}
