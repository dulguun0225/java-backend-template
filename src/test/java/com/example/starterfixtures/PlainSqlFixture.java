package com.example.starterfixtures;

import org.jooq.DSLContext;

class PlainSqlFixture {
    int run(DSLContext dsl) {
        return dsl.execute("delete from greeting");
    }
}
