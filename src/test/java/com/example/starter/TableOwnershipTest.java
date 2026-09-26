package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.starter.db.Tables;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Table ownership: one feature package owns each table, and only that owner writes it (ai-maintainer
 * <i>each table written only by the module that owns it</i>). Any feature may read any table through the
 * generated jOOQ tree, which {@link LayeringArchTest} names shared infrastructure; it still never reads through
 * another feature's classes, which {@code LayeringArchTest} forbids.
 *
 * <p>A method-scoped bytecode approximation, like the ban list's versioned-update and id-ordering rules: a
 * method that starts a write — a {@code DSLContext} or {@code DSL} insert, update, delete, merge, truncate,
 * batch or load, or any {@code VersionedUpdate} call — may name no table its feature does not own. ArchUnit
 * counts an access inside a lambda toward the method that declares it, so a {@code tx.write(dsl -> ...)} body
 * is checked as part of its service method. So a method that reads another feature's table and writes its own
 * is reported too, even with the read in its own {@code tx.read}: read the other table in a method that starts
 * no write. What it does not reach: a table or record held in a field or variable declared outside the writing
 * method, or built in another method and passed in as a plain {@code Table<?>}, is not named by the method that
 * writes it.
 *
 * <p>A new table with no owner row fails {@link #everyTableHasAnOwner}, so the map below cannot go stale.
 */
class TableOwnershipTest {

    /** Generated {@code Tables} constant name to the feature package that owns the table. */
    private static final Map<String, String> OWNERS = Map.of("GREETING", "greeting");

    private static final String BASE = BanListArchTest.BASE;
    private static final String GENERATED_PREFIX = BASE + ".db.";
    private static final String VERSIONED_UPDATE = BASE + ".platform.VersionedUpdate";

    /** The {@code DSLContext} and {@code DSL} methods that start a statement which changes rows. */
    private static final Set<String> WRITE_ENTRIES = Set.of(
            "insertInto",
            "insertQuery",
            "update",
            "updateQuery",
            "delete",
            "deleteFrom",
            "deleteQuery",
            "mergeInto",
            "truncate",
            "truncateTable",
            "batchInsert",
            "batchUpdate",
            "batchStore",
            "batchDelete",
            "batchMerge",
            "executeInsert",
            "executeUpdate",
            "executeDelete",
            "loadInto");

    /** Fixture features sit one level below this package: {@code ownership.greeting} and {@code ownership.reader}. */
    private static final String FIXTURE_BASE = BanListNegativeControlTest.FIXTURES_PACKAGE + ".ownership";

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    private static final JavaClasses FIXTURES = new ClassFileImporter().importPackages(FIXTURE_BASE);

    @Test
    void everyTableHasAnOwner() {
        Set<String> constants = new TreeSet<>();
        for (Field field : Tables.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                constants.add(field.getName());
            }
        }
        assertThat(constants)
                .as("reflection found no table constants in Tables")
                .isNotEmpty();
        assertThat(constants)
                .as("every generated table must carry an owning feature package in TableOwnershipTest.OWNERS")
                .isEqualTo(new TreeSet<>(OWNERS.keySet()));
    }

    @Test
    void onlyTheOwnerWritesATable() {
        assertThat(violations(MAIN, BASE))
                .as("only the owning feature writes a table; a method that reads another feature's table and"
                        + " writes its own is reported too, so read the other table in a method that starts no write")
                .isEmpty();
    }

    @Test
    void theRuleReportsAForeignWriteAndNothingElse() {
        // The negative control this test needs: BanListNegativeControlTest only reaches BanListArchTest.
        // Feature "reader" reads GREETING in one fixture and writes it in another; feature "greeting" writes it.
        assertThat(FIXTURES.size())
                .as("the ownership fixtures package imported nothing")
                .isGreaterThan(0);
        assertThat(violations(FIXTURES, FIXTURE_BASE))
                .as("only the write by a feature that does not own the table is reported")
                .containsExactly(FIXTURE_BASE
                        + ".reader.WritesAnotherFeaturesTable.insert (feature reader) writes and names GREETING,"
                        + " owned by greeting");
    }

    private static List<String> violations(JavaClasses classes, String base) {
        List<String> out = new ArrayList<>();
        for (JavaClass type : classes) {
            String name = type.getName();
            if (name.startsWith(GENERATED_PREFIX) || name.startsWith(BASE + ".platform.")) {
                continue;
            }
            String feature = featureOf(name, base);
            for (JavaCodeUnit unit : type.getCodeUnits()) {
                if (!writes(unit)) {
                    continue;
                }
                String where = unit.getOwner().getName() + "." + unit.getName();
                for (String constant : tablesNamedBy(unit)) {
                    String owner = OWNERS.get(constant);
                    if (feature == null) {
                        out.add(where + " writes and names " + constant + " from outside any feature package");
                    } else if (!feature.equals(owner)) {
                        out.add(where + " (feature " + feature + ") writes and names " + constant + ", owned by "
                                + owner);
                    }
                }
            }
        }
        return out;
    }

    private static boolean writes(JavaCodeUnit unit) {
        return Stream.concat(unit.getMethodCallsFromSelf().stream(), unit.getMethodReferencesFromSelf().stream())
                .anyMatch(TableOwnershipTest::startsAWrite);
    }

    private static boolean startsAWrite(JavaAccess<?> access) {
        JavaClass owner = access.getTarget().getOwner();
        if (owner.getName().equals(VERSIONED_UPDATE)) {
            return true;
        }
        return WRITE_ENTRIES.contains(access.getTarget().getName())
                && (owner.isAssignableTo(DSLContext.class) || owner.getName().equals("org.jooq.impl.DSL"));
    }

    /** Every owned table the code unit names: a {@code Tables} constant, a table or record class, a signature type. */
    private static Set<String> tablesNamedBy(JavaCodeUnit unit) {
        Set<String> out = new TreeSet<>();
        for (JavaFieldAccess access : unit.getFieldAccesses()) {
            String owner = access.getTarget().getOwner().getName();
            String field = access.getTarget().getName();
            if (owner.equals(GENERATED_PREFIX + "Tables") && OWNERS.containsKey(field)) {
                out.add(field);
            } else {
                addIfTable(out, owner);
            }
        }
        for (JavaAccess<?> access : Stream.concat(
                        Stream.concat(
                                unit.getMethodCallsFromSelf().stream(), unit.getConstructorCallsFromSelf().stream()),
                        unit.getMethodReferencesFromSelf().stream())
                .toList()) {
            addIfTable(out, access.getTarget().getOwner().getName());
        }
        for (JavaClass parameter : unit.getRawParameterTypes()) {
            addIfTable(out, parameter.getName());
        }
        addIfTable(out, unit.getRawReturnType().getName());
        return out;
    }

    private static void addIfTable(Set<String> out, String className) {
        String constant = constantFor(className);
        if (constant != null) {
            out.add(constant);
        }
    }

    /** The feature package: the segment directly below {@code base}, or null when there is none. */
    private static @Nullable String featureOf(String className, String base) {
        if (!className.startsWith(base + ".")) {
            return null;
        }
        String rest = className.substring(base.length() + 1);
        int dot = rest.indexOf('.');
        return dot < 0 ? null : rest.substring(0, dot);
    }

    /** Maps {@code db.tables.Greeting} and {@code db.tables.records.GreetingRecord} to the constant. */
    private static @Nullable String constantFor(String className) {
        String simple;
        if (className.startsWith(GENERATED_PREFIX + "tables.records.") && className.endsWith("Record")) {
            simple = className.substring((GENERATED_PREFIX + "tables.records.").length(), className.length() - 6);
        } else if (className.startsWith(GENERATED_PREFIX + "tables.")
                && className.indexOf('.', (GENERATED_PREFIX + "tables.").length()) < 0) {
            simple = className.substring((GENERATED_PREFIX + "tables.").length());
        } else {
            return null;
        }
        String constant = camelToUpperSnake(simple);
        return OWNERS.containsKey(constant) ? constant : null;
    }

    private static String camelToUpperSnake(String simpleName) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < simpleName.length(); i++) {
            char c = simpleName.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                out.append('_');
            }
            out.append(Character.toUpperCase(c));
        }
        return out.toString().toUpperCase(Locale.ROOT);
    }
}
