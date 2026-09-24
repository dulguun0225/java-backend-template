package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * One violating fixture per rule, asserted. ArchUnit's own empty-should guard is one property away from being
 * disabled and does not cover an importer pointed at the wrong path, so a rule that would pass over nothing
 * is caught here instead: every {@code static final ArchRule} in {@link BanListArchTest} is evaluated over the
 * fixtures package ({@code com.example.starterfixtures}, test sources only) and must report a violation.
 */
class BanListNegativeControlTest {

    static final String FIXTURES_PACKAGE = "com.example.starterfixtures";

    private static final JavaClasses FIXTURES = new ClassFileImporter().importPackages(FIXTURES_PACKAGE);

    @Test
    void everyBanRuleCatchesItsFixture() throws IllegalAccessException {
        List<String> silent = new ArrayList<>();
        int rules = 0;
        for (Field field : BanListArchTest.class.getDeclaredFields()) {
            if (!ArchRule.class.isAssignableFrom(field.getType()) || !Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            rules++;
            field.setAccessible(true);
            ArchRule rule = (ArchRule) field.get(null);
            if (!rule.evaluate(FIXTURES).hasViolation()) {
                silent.add(field.getName());
            }
        }
        assertThat(rules)
                .as("reflection found no ArchRule fields in BanListArchTest")
                .isGreaterThanOrEqualTo(10);
        assertThat(FIXTURES.size()).as("the fixtures package imported nothing").isGreaterThan(0);
        assertThat(silent)
                .as("every ban rule must find a violation in " + FIXTURES_PACKAGE + "; a rule that finds none has "
                        + "no proof it can fire. Add a fixture for each rule listed")
                .isEmpty();
    }

    /**
     * The loop above proves the request-body rule fires <em>somewhere</em> in the fixtures package; this pins it
     * to {@code RawRequestBodyFixture}, to its record-typed body, and not to its second handler, whose body binds
     * as {@code BoundBody}.
     */
    @Test
    void theRequestBodyRuleFiresOnItsOwnFixture() {
        String report = BanListArchTest.REQUEST_BODIES_BIND_THROUGH_BOUND_BODY
                .evaluate(FIXTURES)
                .getFailureReport()
                .toString();
        assertThat(report)
                .contains(FIXTURES_PACKAGE + ".RawRequestBodyFixture.create")
                .contains("parameter 0 is a @RequestBody of " + FIXTURES_PACKAGE + ".RawRequestBodyFixture$CreateBody")
                .doesNotContain("RawRequestBodyFixture.update");
    }
}
