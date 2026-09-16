package com.example.starterfixtures;

import static com.example.starter.db.Tables.GREETING;

import org.jooq.DSLContext;

class OffsetPaginationFixture {
    void page(DSLContext dsl) {
        dsl.selectFrom(GREETING)
                .orderBy(GREETING.CREATED_AT)
                .limit(20)
                .offset(40)
                .fetch();
    }
}
