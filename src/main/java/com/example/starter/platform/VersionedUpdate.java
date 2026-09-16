package com.example.starter.platform;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UpdateConditionStep;

/**
 * The one construct that renders {@code UPDATE} on a table carrying a {@code version} column
 * (java-backend-api <i>The guarded version-column update</i>). It executes exactly one statement:
 *
 * <pre>{@code update <table> set <changes>, version = version + 1 where id = ? and version = ?}</pre>
 *
 * <p>Zero affected rows is a signal, never a no-op: it means the row is gone or somebody else moved it on.
 * The caller learns which through {@link Outcome}, and must pass the version it read inside the same
 * transaction — a version read in an earlier transaction proves nothing.
 *
 * <p>This class is never given a {@link java.time.Clock} and never writes {@code modified_at} or
 * {@code modified_by} itself; the caller puts them in {@code changes} so the audit stamp stays visible at the
 * call site. {@code BanListArchTest.versionedTableUpdatesGoThroughHelper} makes every other spelling
 * unwritable.
 */
public final class VersionedUpdate {

    private VersionedUpdate() {}

    /** What the guarded update did. */
    public sealed interface Outcome {

        /** The row moved from {@code expectedVersion} to {@code newVersion}. */
        record Applied(int newVersion) implements Outcome {}

        /** The row exists but is at {@code currentVersion}; the caller's precondition is stale. */
        record Stale(int currentVersion) implements Outcome {}

        /** No row with that id; it was deleted. */
        record Absent() implements Outcome {}
    }

    public static <R extends Record> Outcome apply(
            DSLContext dsl,
            Table<R> table,
            TableField<R, UUID> idField,
            TableField<R, Integer> versionField,
            UUID id,
            int expectedVersion,
            Map<? extends Field<?>, ?> changes) {
        int affected = statement(dsl, table, idField, versionField, id, expectedVersion, changes)
                .execute();
        if (affected == 1) {
            return new Outcome.Applied(expectedVersion + 1);
        }
        if (affected > 1) {
            throw new IllegalStateException(
                    "a versioned update affected " + affected + " rows; the id is a primary key");
        }
        Optional<Integer> current =
                dsl.select(versionField).from(table).where(idField.eq(id)).fetchOptional(versionField);
        return current.<Outcome>map(Outcome.Stale::new).orElseGet(Outcome.Absent::new);
    }

    /**
     * The statement {@link #apply} executes, rendered but not run. Package-private so
     * {@code VersionedUpdateTest} can assert the SQL text without a database.
     */
    static <R extends Record> UpdateConditionStep<R> statement(
            DSLContext dsl,
            Table<R> table,
            TableField<R, UUID> idField,
            TableField<R, Integer> versionField,
            UUID id,
            int expectedVersion,
            Map<? extends Field<?>, ?> changes) {
        return dsl.update(table)
                .set(changes)
                .set(versionField, versionField.plus(1))
                .where(idField.eq(id).and(versionField.eq(expectedVersion)));
    }
}
