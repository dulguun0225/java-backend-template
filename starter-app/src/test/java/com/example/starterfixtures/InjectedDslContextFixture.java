package com.example.starterfixtures;

import org.jooq.DSLContext;

class InjectedDslContextFixture {
    private final DSLContext dsl;

    InjectedDslContextFixture(DSLContext dsl) {
        this.dsl = dsl;
    }

    DSLContext dsl() {
        return dsl;
    }
}
