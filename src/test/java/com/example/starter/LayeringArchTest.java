package com.example.starter;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.Slice;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Package layering. The platform tier depends on no feature; features depend on the platform tier and never
 * on each other; the generated jOOQ tree is shared infrastructure any feature may read. A feature package is a
 * direct child of the base package whose name is neither {@code platform} nor {@code db}, compared whole.
 *
 * <p>This is the strictest allowed map java-backend-rules <i>The module boundary is enforced by ArchUnit, not by
 * package naming</i> admits: no feature-to-feature edge, each feature depending on the shared tier only. With no
 * edge there is no cycle and no reference into another feature's internals, so the directive's cycle and
 * {@code api}-only rules hold by consequence. The first feature allowed to call another is a committed change to
 * this map, and from then the three rules are written out here.
 *
 * <p>Each rule is a {@code static ArchRule} factory over a base package, checked once over the main code and
 * once over the fixture tree {@code com.example.starterfixtures.layering} (test sources only, outside the main
 * import), where it must report exactly the fixture classes written to break it. The main code has one feature
 * package, so the feature rule could report nothing there; the fixture tree has four.
 */
class LayeringArchTest {

    /** The direct children of the base package that are not features: the platform tier and the generated tree. */
    static final List<String> NOT_FEATURES = List.of("platform", "db");

    static final String FIXTURE_BASE = BanListNegativeControlTest.FIXTURES_PACKAGE + ".layering";

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BanListArchTest.BASE);

    private static final JavaClasses FIXTURES = new ClassFileImporter().importPackages(FIXTURE_BASE);

    static ArchRule platformDependsOnNoFeature(String base) {
        String platform = base + ".platform..";
        String generated = base + ".db..";
        return noClasses()
                .that()
                .resideInAPackage(platform)
                .should()
                .dependOnClassesThat(resideInAPackage(base + "..").and(resideOutsideOfPackages(platform, generated)))
                .because("the platform tier is the foundation; a feature dependency would invert the layering");
    }

    static ArchRule featuresDoNotDependOnEachOther(String base) {
        return slices().matching(base + ".(*)..")
                .that(DescribedPredicate.<Slice>describe(
                        "are feature slices (named none of " + NOT_FEATURES + ")",
                        slice -> !NOT_FEATURES.contains(slice.getNamePart(1))))
                .should()
                .notDependOnEachOther()
                .because(
                        "features integrate through the platform tier or over the wire, never through each other's classes");
    }

    static ArchRule controllersLiveInFeaturePackages(String base) {
        return classes()
                .that()
                .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should()
                .resideOutsideOfPackages(base + ".platform..", base + ".db..")
                .because("the platform tier has no HTTP surface");
    }

    @Test
    void platformDependsOnNoFeature() {
        platformDependsOnNoFeature(BanListArchTest.BASE).check(MAIN);
    }

    @Test
    void featuresDoNotDependOnEachOther() {
        featuresDoNotDependOnEachOther(BanListArchTest.BASE).check(MAIN);
    }

    @Test
    void controllersLiveInFeaturePackages() {
        controllersLiveInFeaturePackages(BanListArchTest.BASE).check(MAIN);
    }

    @Test
    void everyLayeringRuleReportsTheFixtureTree() throws IllegalAccessException, InvocationTargetException {
        assertThat(FIXTURES.size())
                .as("the layering fixtures package imported nothing")
                .isGreaterThan(0);
        List<String> silent = new ArrayList<>();
        int rules = 0;
        for (Method factory : LayeringArchTest.class.getDeclaredMethods()) {
            if (!ArchRule.class.equals(factory.getReturnType()) || !Modifier.isStatic(factory.getModifiers())) {
                continue;
            }
            rules++;
            ArchRule rule = (ArchRule) factory.invoke(null, FIXTURE_BASE);
            if (!rule.evaluate(FIXTURES).hasViolation()) {
                silent.add(factory.getName());
            }
        }
        assertThat(rules)
                .as("reflection found no ArchRule factories in LayeringArchTest")
                .isGreaterThanOrEqualTo(3);
        assertThat(silent)
                .as("every layering rule must report a violation in " + FIXTURE_BASE + "; add a fixture for each")
                .isEmpty();
    }

    /** {@code PlatformCallsFeature} reaches into {@code greeting}; {@code PlatformReadsGeneratedTree} reads {@code db}. */
    @Test
    void thePlatformRuleReportsOnlyTheCallIntoAFeature() {
        List<String> details = details(platformDependsOnNoFeature(FIXTURE_BASE));
        assertThat(details)
                .as("the platform rule reported nothing over its fixture")
                .isNotEmpty();
        assertThat(details)
                .as("only the platform class that depends on a feature is reported")
                .allSatisfy(detail -> assertThat(detail)
                        .startsWith("Method <" + FIXTURE_BASE + ".platform.PlatformCallsFeature.")
                        .contains("<" + FIXTURE_BASE + ".greeting.GreetingService"));
    }

    /**
     * Three features depend on {@code greeting} and each is reported, {@code feedback} and {@code partnerplatform}
     * included; no feature's dependency on {@code platform} or {@code db} is, and neither is the platform's on
     * {@code greeting}, which {@link #platformDependsOnNoFeature} owns.
     */
    @Test
    void theFeatureRuleReportsEveryFeatureToFeatureDependencyByExactSliceName() {
        assertThat(details(featuresDoNotDependOnEachOther(FIXTURE_BASE)).stream()
                        .map(detail -> detail.lines().findFirst().orElse(""))
                        .toList())
                .as("each feature-to-feature dependency is reported, matched by slice name, not substring")
                .containsExactlyInAnyOrder(
                        "Slice billing depends on Slice greeting:",
                        "Slice feedback depends on Slice greeting:",
                        "Slice partnerplatform depends on Slice greeting:");
    }

    /** A controller in the fixture platform tier is reported; the one in fixture feature {@code greeting} is not. */
    @Test
    void theControllerRuleReportsOnlyThePlatformController() {
        assertThat(details(controllersLiveInFeaturePackages(FIXTURE_BASE)))
                .as("only the controller in the platform tier is reported")
                .singleElement()
                .asString()
                .startsWith("Class <" + FIXTURE_BASE + ".platform.PlatformController> does not reside outside");
    }

    private static List<String> details(ArchRule rule) {
        return rule.evaluate(FIXTURES).getFailureReport().getDetails();
    }
}
