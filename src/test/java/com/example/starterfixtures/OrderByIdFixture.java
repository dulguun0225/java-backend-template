package com.example.starterfixtures;

import static com.example.starter.db.Tables.GREETING;

import org.jooq.DSLContext;

/** Orders by the id column, which is not an ordering: a time-ordered key is monotonic per generator only. */
class OrderByIdFixture {
    void listed(DSLContext dsl) {
        dsl.selectFrom(GREETING).orderBy(GREETING.ID).fetch();
    }
}
