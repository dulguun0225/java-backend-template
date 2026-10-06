package com.example.starter;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.library.dependencies.Slice;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Package layering. The platform tier depends on nothing in the base package but itself and the generated jOOQ
 * tree; features depend on the platform tier and the generated tree and never on each other; the generated tree
 * depends on nothing in the base package outside itself; every controller sits in a feature package. A feature
 * package is a direct child of the base package whose name is neither {@code platform} nor {@code db}, compared
 * whole. A class directly in the base package belongs to no slice.
 *
 * <p>This is the strictest allowed map java-backend-rules <i>The module boundary is enforced by ArchUnit, not by
 * package naming</i> admits: no feature-to-feature edge, each feature depending on the shared tier only, and
 * inside the shared tier one direction, the platform tier into the generated tree. Every module edge then points
 * from a feature into the shared tier or from the platform tier into the generated tree, so there is no cycle and
 * no reference into another feature's internals, and the directive's cycle and {@code api}-only rules hold by
 * consequence. The first feature allowed to call another, or a generated class allowed to name a class outside
 * the tree (a jOOQ forced type's converter), is a committed change to this map, and from then the three rules are
 * written out here.
 *
 * <p>Each rule is a {@code static} factory returning an {@link ArchRule} over a base package. Every factory is
 * found by reflection and checked over the main code by {@link #everyLayeringRuleHoldsOverTheMainCode} and over
 * the fixture tree {@code com.example.starterfixtures.layering} (test sources only, outside the main import) by
 * {@link #everyLayeringRuleReportsTheFixtureTree}, so a factory is never one of the two without the other. The
 * main code has one feature package, so the feature rule could report nothing there; the fixture tree has four.
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
                .because("the platform tier is the foundation; a dependency on a feature, or on a class directly in"
                        + " the base package, would invert the layering");
    }

    static ArchRule generatedTreeDependsOnNothingOutsideIt(String base) {
        String generated = base + ".db..";
        return noClasses()
                .that()
                .resideInAPackage(generated)
                .should()
                .dependOnClassesThat(resideInAPackage(base + "..").and(resideOutsideOfPackage(generated)))
                .because("the generated tree is shared and every tier reads it; a generated class naming a feature"
                        + " or the platform tier closes a cycle through it, an edge the map does not hold");
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
                .areMetaAnnotatedWith("org.springframework.stereotype.Controller")
                .should()
                .resideInAPackage(base + ".*..")
                .andShould()
                .resideOutsideOfPackages(base + ".platform..", base + ".db..")
                .because("an endpoint belongs to a feature; the platform tier, the generated tree and the base"
                        + " package itself have no HTTP surface");
    }

    @Test
    void everyLayeringRuleHoldsOverTheMainCode() throws IllegalAccessException, InvocationTargetException {
        assertThat(MAIN.stream().map(JavaClass::getName))
                .as("the main import holds the platform tier and the application class")
                .contains(BanListArchTest.BASE + ".platform.Tx", BanListArchTest.BASE + ".Application");
        List<String> failures = new ArrayList<>();
        for (Method factory : ruleFactories()) {
            EvaluationResult result = ((ArchRule) factory.invoke(null, BanListArchTest.BASE)).evaluate(MAIN);
            if (result.hasViolation()) {
                failures.add(factory.getName() + ":\n" + result.getFailureReport());
            }
        }
        assertThat(failures).as("every layering rule holds over the main code").isEmpty();
    }

    @Test
    void everyLayeringRuleReportsTheFixtureTree() throws IllegalAccessException, InvocationTargetException {
        assertThat(FIXTURES.size())
                .as("the layering fixtures package imported nothing")
                .isGreaterThan(0);
        List<String> silent = new ArrayList<>();
        for (Method factory : ruleFactories()) {
            if (!((ArchRule) factory.invoke(null, FIXTURE_BASE))
                    .evaluate(FIXTURES)
                    .hasViolation()) {
                silent.add(factory.getName());
            }
        }
        assertThat(silent)
                .as("every layering rule must report a violation in " + FIXTURE_BASE + "; add a fixture for each")
                .isEmpty();
    }

    /**
     * {@code PlatformCallsFeature} reaches into {@code greeting} and {@code PlatformCallsTheBasePackage} into a
     * class directly in the base package; {@code PlatformReadsGeneratedTree} reads {@code db}, which is allowed.
     */
    @Test
    void thePlatformRuleReportsOnlyItsCallsIntoAFeatureAndTheBasePackage() {
        List<String> details = details(platformDependsOnNoFeature(FIXTURE_BASE));
        assertThat(details)
                .as("each platform class that depends on a feature or on the base package is reported")
                .anySatisfy(detail -> assertThat(detail)
                        .startsWith("Method <" + FIXTURE_BASE + ".platform.PlatformCallsFeature.")
                        .contains("<" + FIXTURE_BASE + ".greeting.GreetingService"))
                .anySatisfy(detail -> assertThat(detail)
                        .startsWith("Method <" + FIXTURE_BASE + ".platform.PlatformCallsTheBasePackage.")
                        .contains("<" + FIXTURE_BASE + ".RootWiring"));
        assertThat(details)
                .as("nothing else is reported: not the platform tier's read of the generated tree")
                .allSatisfy(detail -> assertThat(detail)
                        .matches("(?s)Method <\\Q" + FIXTURE_BASE
                                + ".platform.\\E(PlatformCallsFeature|PlatformCallsTheBasePackage)\\..*"));
    }

    /**
     * {@code GeneratedTableNamesAFeature} names {@code greeting} and {@code GeneratedTableNamesThePlatform} names
     * the platform tier, as a jOOQ forced type's converter would; a reference inside the tree is not reported.
     */
    @Test
    void theGeneratedTreeRuleReportsOnlyItsReferencesOutOfTheTree() {
        List<String> details = details(generatedTreeDependsOnNothingOutsideIt(FIXTURE_BASE));
        assertThat(details)
                .as("each generated class that names a class outside the tree is reported")
                .anySatisfy(detail -> assertThat(detail)
                        .startsWith("Method <" + FIXTURE_BASE + ".db.GeneratedTableNamesAFeature.")
                        .contains("<" + FIXTURE_BASE + ".greeting.GreetingService"))
                .anySatisfy(detail -> assertThat(detail)
                        .startsWith("Method <" + FIXTURE_BASE + ".db.GeneratedTableNamesThePlatform.")
                        .contains("<" + FIXTURE_BASE + ".platform.PlatformReadsGeneratedTree"));
        assertThat(details)
                .as("nothing else is reported: not a reference inside the tree")
                .allSatisfy(detail -> assertThat(detail)
                        .matches("(?s)Method <\\Q" + FIXTURE_BASE
                                + ".db.\\E(GeneratedTableNamesAFeature|GeneratedTableNamesThePlatform)\\..*")
                        .doesNotContain("<" + FIXTURE_BASE + ".db.GeneratedTable."));
    }

    /**
     * Three features depend on {@code greeting} and each is reported, {@code feedback} and {@code partnerplatform}
     * included; no feature's dependency on {@code platform} or {@code db} is, and neither is the platform's or the
     * generated tree's on {@code greeting}, which their own rules own, nor {@code RootController}'s, since the base
     * package belongs to no slice.
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

    /**
     * The {@code @RestController} in the fixture platform tier and the {@code @Controller} directly in the fixture
     * base package are reported; the one in fixture feature {@code greeting} is not.
     */
    @Test
    void theControllerRuleReportsEveryControllerOutsideAFeature() {
        assertThat(details(controllersLiveInFeaturePackages(FIXTURE_BASE)))
                .as("only the controllers outside every feature package are reported")
                .satisfiesExactlyInAnyOrder(
                        detail -> assertThat(detail).startsWith("Class <" + FIXTURE_BASE + ".RootController> "),
                        detail -> assertThat(detail)
                                .startsWith("Class <" + FIXTURE_BASE + ".platform.PlatformController> "));
    }

    /**
     * Every method of this class that returns an {@link ArchRule} or a subtype of it, such as the {@code SliceRule}
     * a slices rule builds, so a narrower declared return type cannot drop a rule from either check. Each must be a
     * {@code static} factory taking the base package, so one that is not fails here instead of being skipped.
     */
    private static List<Method> ruleFactories() {
        List<Method> factories = Arrays.stream(LayeringArchTest.class.getDeclaredMethods())
                .filter(method -> ArchRule.class.isAssignableFrom(method.getReturnType()))
                .sorted(Comparator.comparing(Method::getName))
                .toList();
        assertThat(factories)
                .as("reflection found no ArchRule factories in LayeringArchTest")
                .isNotEmpty();
        assertThat(factories)
                .as("every method returning an ArchRule is a static factory taking the base package")
                .allSatisfy(factory -> assertThat(Modifier.isStatic(factory.getModifiers())
                                && Arrays.equals(factory.getParameterTypes(), new Class<?>[] {String.class}))
                        .as(factory.getName() + " is a static factory taking the base package")
                        .isTrue());
        return factories;
    }

    private static List<String> details(ArchRule rule) {
        return rule.evaluate(FIXTURES).getFailureReport().getDetails();
    }
}
