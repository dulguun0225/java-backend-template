package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.starter.db.Tables;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Table ownership: one feature package owns each table, and only that owner — or a package explicitly
 * licensed to read across features — may name it (ai-maintainer <i>no cross-module data access</i>).
 * Cross-feature reads go through generated jOOQ, never through another feature's classes, which
 * {@link LayeringArchTest} already forbids.
 *
 * <p>A new table with no owner row fails {@link #everyTableHasAnOwner}, so the map below cannot go stale.
 */
class TableOwnershipTest {

    /** Generated {@code Tables} constant name to the feature package that owns the table. */
    private static final Map<String, String> OWNERS = Map.of("GREETING", "greeting");

    /**
     * Feature packages licensed to read tables they do not own. Empty here: a project names the package it
     * licenses to read across features, if it has one, and says why in the same commit.
     */
    private static final Set<String> LICENSED_READERS = Set.of();

    private static final String BASE = BanListArchTest.BASE;
    private static final String GENERATED_PREFIX = BASE + ".db.";

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    private static final JavaClasses FIXTURES =
            new ClassFileImporter().importPackages(BanListNegativeControlTest.FIXTURES_PACKAGE);

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
    void onlyTheOwnerTouchesATable() {
        assertThat(violations(MAIN)).isEmpty();
    }

    @Test
    void theRuleFiresOnAFixture() {
        // The negative control this test needs: BanListNegativeControlTest only reaches BanListArchTest.
        // OffsetPaginationFixture touches GREETING from outside any owning feature package.
        assertThat(violations(FIXTURES))
                .as("the ownership predicate must fire on the fixtures package")
                .isNotEmpty();
    }

    private static List<String> violations(JavaClasses classes) {
        List<String> out = new ArrayList<>();
        for (JavaClass type : classes) {
            String name = type.getName();
            if (name.startsWith(GENERATED_PREFIX)
                    || name.startsWith(BASE + ".platform.")
                    || name.equals(BASE + ".db.Tables")) {
                continue;
            }
            String feature = featureOf(name);
            for (JavaFieldAccess access : type.getFieldAccessesFromSelf()) {
                String owner = access.getTarget().getOwner().getName();
                String constant = access.getTarget().getName();
                if (owner.startsWith(GENERATED_PREFIX) && OWNERS.containsKey(constant)) {
                    check(out, name, feature, constant);
                }
            }
            for (JavaClass dependency : type.getDirectDependenciesFromSelf().stream()
                    .map(d -> d.getTargetClass())
                    .toList()) {
                String constant = constantFor(dependency.getName());
                if (constant != null) {
                    check(out, name, feature, constant);
                }
            }
        }
        return out;
    }

    private static void check(List<String> out, String className, @Nullable String feature, String constant) {
        String owner = OWNERS.get(constant);
        if (feature == null) {
            out.add(className + " touches " + constant + " from outside any feature package");
        } else if (!feature.equals(owner) && !LICENSED_READERS.contains(feature)) {
            out.add(className + " (feature " + feature + ") touches " + constant + " owned by " + owner);
        }
    }

    /** The feature package: the segment directly below the base package, or null when there is none. */
    private static @Nullable String featureOf(String className) {
        if (!className.startsWith(BASE + ".")) {
            return null;
        }
        String rest = className.substring(BASE.length() + 1);
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
