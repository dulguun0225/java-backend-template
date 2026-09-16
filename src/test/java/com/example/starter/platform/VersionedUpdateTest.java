package com.example.starter.platform;

import static com.example.starter.db.Tables.GREETING;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.starter.db.tables.records.GreetingRecord;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.UpdateConditionStep;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The guarded update's two contracts, both without a database: the SQL it renders (the version increment and
 * the two-predicate guard are the whole point of the helper) and the {@link VersionedUpdate.Outcome}
 * classification of zero affected rows, which is a signal and never a no-op.
 */
class VersionedUpdateTest {

    private static final UUID ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

    @Test
    void rendersTheIncrementAndTheTwoPredicateGuard() {
        DSLContext dsl = DSL.using(SQLDialect.POSTGRES);
        UpdateConditionStep<GreetingRecord> statement = VersionedUpdate.statement(
                dsl, GREETING, GREETING.ID, GREETING.VERSION, ID, 3, Map.of(GREETING.NAME, "Ada"));

        // jOOQ qualifies every column with the table and binds the literals; the assertions pin the
        // increment and the two-predicate guard, not the quoting.
        assertThat(statement.getSQL())
                .contains("\"version\" = (\"greeting\".\"version\" + ?)")
                .contains("where (\"greeting\".\"id\" = cast(? as uuid) and \"greeting\".\"version\" = ?)");
        assertThat(dsl.renderInlined(statement))
                .contains("\"version\" = (\"greeting\".\"version\" + 1)")
                .contains("\"greeting\".\"version\" = 3");
    }

    @Test
    void oneAffectedRowIsApplied() {
        DSLContext dsl = mockDsl(new MockResult[] {new MockResult(1)});

        VersionedUpdate.Outcome outcome = apply(dsl);

        assertThat(outcome).isEqualTo(new VersionedUpdate.Outcome.Applied(4));
    }

    @Test
    void zeroRowsWithAPresentRowIsStale() {
        DSLContext dsl = mockDsl(new MockResult[] {new MockResult(0), versionRow(5)});

        VersionedUpdate.Outcome outcome = apply(dsl);

        assertThat(outcome).isEqualTo(new VersionedUpdate.Outcome.Stale(5));
    }

    @Test
    void zeroRowsWithNoRowIsAbsent() {
        DSLContext dsl = mockDsl(new MockResult[] {new MockResult(0), versionRow(null)});

        VersionedUpdate.Outcome outcome = apply(dsl);

        assertThat(outcome).isEqualTo(new VersionedUpdate.Outcome.Absent());
    }

    private static VersionedUpdate.Outcome apply(DSLContext dsl) {
        return VersionedUpdate.apply(dsl, GREETING, GREETING.ID, GREETING.VERSION, ID, 3, Map.of(GREETING.NAME, "Ada"));
    }

    /** A re-read result carrying one version row, or no row at all when {@code version} is null. */
    private static MockResult versionRow(@Nullable Integer version) {
        DSLContext render = DSL.using(SQLDialect.POSTGRES);
        Field<Integer> field = GREETING.VERSION;
        Result<Record1<Integer>> result = render.newResult(field);
        if (version != null) {
            Record1<Integer> record = render.newRecord(field);
            record.value1(version);
            result.add(record);
        }
        return new MockResult(result.size(), result);
    }

    private static DSLContext mockDsl(MockResult[] scripted) {
        MockDataProvider provider = new MockDataProvider() {
            private int call;

            @Override
            public MockResult[] execute(MockExecuteContext context) {
                MockResult next = scripted[Math.min(call, scripted.length - 1)];
                call++;
                return new MockResult[] {next};
            }
        };
        return DSL.using(new MockConnection(provider), SQLDialect.POSTGRES);
    }
}
