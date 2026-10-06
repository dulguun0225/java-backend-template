package com.example.starter;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.conditions.ArchConditions.onlyHaveDependenciesWhere;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Package layering. A module is a direct child package of the base package, compared by whole name: the platform
 * tier ({@code platform}), the generated jOOQ tree ({@code db}), and every other one a feature. A class directly in
 * the base package belongs to no module. The rules:
 *
 * <ul>
 *   <li>no dependency cycle between modules;
 *   <li>a feature reaches another feature only through classes in that feature's {@code api} package, the package
 *       itself and not a subpackage of it;
 *   <li>every feature-to-feature dependency is listed in {@link #ALLOWED_FEATURE_DEPENDENCIES}, and every line there
 *       is a dependency the code takes;
 *   <li>the platform tier depends on nothing in the base package but itself and the generated tree;
 *   <li>the generated tree depends on nothing in the base package outside itself;
 *   <li>a class directly in the base package depends on no feature;
 *   <li>every controller sits in a feature package.
 * </ul>
 *
 * <p>The first three are java-backend-rules <i>The module boundary is enforced by ArchUnit, not by package
 * naming</i>. The edges into and inside the shared tier are not map lines: every feature may depend on the platform
 * tier and the generated tree, and the platform and generated-tree rules fix the shared tier's direction, the
 * platform tier into the generated tree and never back. The cycle rule does not see the base package, which is no
 * module; no cycle can run through it, since it depends on no feature and neither the platform tier nor the generated tree
 * depends on it.
 *
 * <p>Each rule is a {@code static} factory returning an {@link ArchRule} over a {@link Layout}, the base package
 * and its allowed map. Every factory is found by reflection and checked over the main code by {@link
 * #everyLayeringRuleHoldsOverTheMainCode} and over the fixture tree {@code com.example.starterfixtures.layering}
 * (test sources only, outside the main import), with its own map, by {@link
 * #everyLayeringRuleReportsTheFixtureTree}, so a factory is never one of the two without the other; {@link
 * #everyLayeringRuleIsAFactoryWithATestOfItsOwn} holds that no rule is built outside a factory and that each factory
 * has a test asserting exactly what it reports. The main code has one feature package, so the feature rules could
 * report nothing there; the fixture tree has six.
 */
class LayeringArchTest {

    /**
     * Every feature-to-feature dependency the build allows: one {@code caller -> callee} line each, both named as
     * their package under the base package. Empty: no feature depends on another. A feature that needs another
     * adds one line here, in the same commit as the code that calls it, and calls only the classes the callee
     * puts in its {@code api} package itself. A line no dependency takes fails, so it goes out in the commit that
     * removes the last call; an edge in both directions is a cycle and fails whatever the map says. A feature edge
     * is a line here, never an edit to the rules below.
     */
    static final String ALLOWED_FEATURE_DEPENDENCIES = """
            """;

    /** The direct children of the base package that are not features: the platform tier and the generated tree. */
    static final List<String> NOT_FEATURES = List.of("platform", "db");

    static final String FIXTURE_BASE = BanListNegativeControlTest.FIXTURES_PACKAGE + ".layering";

    /**
     * The fixture tree's own map. {@code orders} and {@code inventory} are allowed in both directions, so only the
     * cycle rule reports them; {@code partnerplatform} is allowed into {@code greeting} and reaches past its
     * {@code api} package, into its internals and into a subpackage of {@code api}; {@code billing} is allowed into
     * {@code greeting} and reaches only its {@code api} package, so nothing reports it. {@code feedback} has no
     * line. The last two lines are taken by no dependency: {@code greeting} does not depend on {@code billing}, and
     * no feature {@code ghost} exists.
     */
    static final String FIXTURE_ALLOWED_FEATURE_DEPENDENCIES = """
            billing -> greeting
            partnerplatform -> greeting
            orders -> inventory
            inventory -> orders
            greeting -> billing
            ghost -> greeting
            """;

    static final Layout MAIN_LAYOUT = new Layout(BanListArchTest.BASE, ALLOWED_FEATURE_DEPENDENCIES);

    static final Layout FIXTURE_LAYOUT = new Layout(FIXTURE_BASE, FIXTURE_ALLOWED_FEATURE_DEPENDENCIES);

    private static final Pattern MAP_LINE = Pattern.compile("([a-z_][a-z0-9_]*) -> ([a-z_][a-z0-9_]*)");

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BanListArchTest.BASE);

    private static final JavaClasses FIXTURES = new ClassFileImporter().importPackages(FIXTURE_BASE);

    /** A base package and its allowed feature-to-feature map, in the form of {@link #ALLOWED_FEATURE_DEPENDENCIES}. */
    record Layout(String base, String allowedFeatureDependencies) {

        /**
         * The map's edges as {@code caller -> callee}. A line that is not one, names the platform tier or the
         * generated tree, names the same feature on both sides, or repeats an earlier line throws, so a malformed
         * map fails every rule that reads it. Whether each line names features that exist, and is taken by a
         * dependency, is {@link #featureDependenciesAreInTheAllowedMap}'s to report, since it needs the classes.
         */
        Set<FeatureEdge> allowedEdges() {
            Set<FeatureEdge> edges = new HashSet<>();
            allowedFeatureDependencies
                    .lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty())
                    .forEach(line -> {
                        Matcher edge = MAP_LINE.matcher(line);
                        if (!edge.matches()) {
                            throw new IllegalArgumentException(
                                    "not a 'caller -> callee' line in the allowed feature map: " + line);
                        }
                        if (NOT_FEATURES.contains(edge.group(1))
                                || NOT_FEATURES.contains(edge.group(2))
                                || edge.group(1).equals(edge.group(2))) {
                            throw new IllegalArgumentException(
                                    "the allowed feature map lists edges between two different features only: " + line);
                        }
                        if (!edges.add(new FeatureEdge(edge.group(1), edge.group(2)))) {
                            throw new IllegalArgumentException("a repeated line in the allowed feature map: " + line);
                        }
                    });
            return Set.copyOf(edges);
        }

        /** The module a class belongs to, its package's first segment under the base package; none directly in it. */
        @Nullable
        String moduleOf(JavaClass javaClass) {
            String packageName = javaClass.getPackageName();
            if (!packageName.startsWith(base + ".")) {
                return null;
            }
            String rest = packageName.substring(base.length() + 1);
            int dot = rest.indexOf('.');
            return dot < 0 ? rest : rest.substring(0, dot);
        }

        @Nullable
        String featureOf(JavaClass javaClass) {
            String module = moduleOf(javaClass);
            return module == null || NOT_FEATURES.contains(module) ? null : module;
        }

        /** The feature a dependency leaves and the one it enters, when they are two different features; else none. */
        @Nullable
        FeatureEdge featureEdge(Dependency dependency) {
            String caller = featureOf(dependency.getOriginClass());
            String callee = featureOf(dependency.getTargetClass());
            return caller == null || callee == null || caller.equals(callee) ? null : new FeatureEdge(caller, callee);
        }

        DescribedPredicate<JavaClass> inAFeature() {
            return DescribedPredicate.describe(
                    "reside in a feature package (a direct child of " + base + " named none of " + NOT_FEATURES + ")",
                    javaClass -> featureOf(javaClass) != null);
        }
    }

    /** A dependency of feature {@code caller} on feature {@code callee}. */
    record FeatureEdge(String caller, String callee) {
        @Override
        public String toString() {
            return caller + " -> " + callee;
        }
    }

    static ArchRule modulesAreFreeOfCycles(Layout layout) {
        return slices().matching(layout.base() + ".(*)..")
                .should()
                .beFreeOfCycles()
                .because("modules form a directed acyclic graph; a cycle makes two modules one, whatever the map"
                        + " allows");
    }

    static ArchRule featuresReachAnotherFeatureOnlyThroughItsApi(Layout layout) {
        return classes()
                .that(layout.inAFeature())
                .should(onlyHaveDependenciesWhere(DescribedPredicate.describe(
                        "the target, when in another feature, resides in that feature's api package",
                        (Dependency dependency) -> {
                            FeatureEdge edge = layout.featureEdge(dependency);
                            return edge == null
                                    || dependency
                                            .getTargetClass()
                                            .getPackageName()
                                            .equals(layout.base() + "." + edge.callee() + ".api");
                        })))
                .because("a feature's api package is its whole surface to other features; everything else in it is"
                        + " internal");
    }

    static ArchRule featureDependenciesAreInTheAllowedMap(Layout layout) {
        return classes()
                .that(layout.inAFeature())
                .should(new MatchesTheAllowedMap(layout))
                .because("every feature-to-feature edge is one committed line in ALLOWED_FEATURE_DEPENDENCIES, added"
                        + " in the commit that needs it and removed with the last code that takes it");
    }

    /**
     * Reports each dependency of one feature on another that the map does not list, and, once every class is
     * checked, each map line no dependency took, a line naming a feature package that does not exist among them.
     * The map is parsed when the condition is built, so a malformed map fails before any class is read.
     */
    private static final class MatchesTheAllowedMap extends ArchCondition<JavaClass> {
        private final Layout layout;
        private final Set<FeatureEdge> allowed;
        private final Set<String> features = new TreeSet<>();
        private final Set<FeatureEdge> taken = new HashSet<>();

        MatchesTheAllowedMap(Layout layout) {
            this(layout, layout.allowedEdges());
        }

        private MatchesTheAllowedMap(Layout layout, Set<FeatureEdge> allowed) {
            super("depend on another feature only over an edge the allowed map lists, "
                    + allowed.stream().map(FeatureEdge::toString).sorted().toList()
                    + ", and take every edge it lists");
            this.layout = layout;
            this.allowed = allowed;
        }

        @Override
        public void init(Collection<JavaClass> featureClasses) {
            features.clear();
            taken.clear();
            for (JavaClass javaClass : featureClasses) {
                String feature = layout.featureOf(javaClass);
                if (feature != null) {
                    features.add(feature);
                }
            }
        }

        @Override
        public void check(JavaClass javaClass, ConditionEvents events) {
            for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                FeatureEdge edge = layout.featureEdge(dependency);
                if (edge == null) {
                    continue;
                }
                taken.add(edge);
                if (!allowed.contains(edge)) {
                    events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                }
            }
        }

        @Override
        public void finish(ConditionEvents events) {
            allowed.stream()
                    .filter(edge -> !taken.contains(edge))
                    .sorted(Comparator.comparing(FeatureEdge::toString))
                    .forEach(edge -> {
                        List<String> unknown = Stream.of(edge.caller(), edge.callee())
                                .filter(feature -> !features.contains(feature))
                                .toList();
                        events.add(SimpleConditionEvent.violated(
                                edge,
                                "Allowed feature map line <" + edge + "> "
                                        + (unknown.isEmpty()
                                                ? "is taken by no dependency; a line goes in with the code that"
                                                        + " takes it and out with the last code that did"
                                                : "names " + unknown + ", not among the feature packages " + features
                                                        + " under " + layout.base())));
                    });
        }
    }

    static ArchRule platformDependsOnNoFeature(Layout layout) {
        String platform = layout.base() + ".platform..";
        String generated = layout.base() + ".db..";
        return noClasses()
                .that()
                .resideInAPackage(platform)
                .should()
                .dependOnClassesThat(
                        resideInAPackage(layout.base() + "..").and(resideOutsideOfPackages(platform, generated)))
                .because("the platform tier is the foundation; a dependency on a feature, or on a class directly in"
                        + " the base package, would invert the layering");
    }

    static ArchRule generatedTreeDependsOnNothingOutsideIt(Layout layout) {
        String generated = layout.base() + ".db..";
        return noClasses()
                .that()
                .resideInAPackage(generated)
                .should()
                .dependOnClassesThat(resideInAPackage(layout.base() + "..").and(resideOutsideOfPackage(generated)))
                .because("the generated tree is shared and every tier reads it; a generated class naming a feature"
                        + " or the platform tier closes a cycle through it");
    }

    static ArchRule basePackageDependsOnNoFeature(Layout layout) {
        return noClasses()
                .that()
                .resideInAPackage(layout.base())
                .should()
                .dependOnClassesThat(layout.inAFeature())
                .because("the application class and the HTTP edge serve every feature and belong to none; a"
                        + " reference into one is a feature edge no map line or api package governs");
    }

    static ArchRule controllersLiveInFeaturePackages(Layout layout) {
        return classes()
                .that()
                .areMetaAnnotatedWith("org.springframework.stereotype.Controller")
                .should()
                .resideInAPackage(layout.base() + ".*..")
                .andShould()
                .resideOutsideOfPackages(layout.base() + ".platform..", layout.base() + ".db..")
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
            EvaluationResult result = ((ArchRule) factory.invoke(null, MAIN_LAYOUT)).evaluate(MAIN);
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
            if (!((ArchRule) factory.invoke(null, FIXTURE_LAYOUT))
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
     * The reflection tests above take any violation in the fixture tree as proof, so a rule could pass them on a
     * violation another rule's fixture causes. This class's own bytecode is read to hold two things: an ArchUnit rule
     * is built nowhere but in a factory reflection finds, so none is checked outside both reflection tests; and each
     * factory is called directly by a {@code @Test} method, the test that asserts exactly what it reports. Whether
     * that test asserts exactly is its own text's to show.
     */
    @Test
    void everyLayeringRuleIsAFactoryWithATestOfItsOwn() {
        JavaClass self =
                new ClassFileImporter().importClasses(LayeringArchTest.class).get(LayeringArchTest.class);
        List<String> factories = ruleFactories().stream().map(Method::getName).toList();
        assertThat(self.getMethodCallsFromSelf().stream()
                        .filter(call -> call.getTargetOwner().isEquivalentTo(ArchRuleDefinition.class)
                                || call.getTargetOwner().isEquivalentTo(SlicesRuleDefinition.class))
                        .map(call -> call.getOrigin().getName())
                        .distinct()
                        .toList())
                .as("the methods that build an ArchUnit rule, each of which must be a factory")
                .isNotEmpty()
                .isSubsetOf(factories);
        assertThat(factories)
                .allSatisfy(factory -> assertThat(
                                self.getMethod(factory, Layout.class).getCallsOfSelf())
                        .as(factory + " is called directly by a @Test method of its own")
                        .anySatisfy(call -> assertThat(call.getOrigin().isAnnotatedWith(Test.class))
                                .isTrue()));
    }

    /**
     * {@code orders} and {@code inventory} call each other through their {@code api} packages, an edge the fixture
     * map allows both ways, and the cycle is still reported. So are the cycles the platform and generated-tree
     * fixtures close through the shared tier, which those rules report too. No cycle involves {@code billing},
     * {@code feedback} or {@code partnerplatform}, which nothing depends on. Each cycle is written from its
     * alphabetically first slice.
     */
    @Test
    void theCycleRuleReportsEveryCycleIncludingOneTheMapAllows() {
        assertThat(details(modulesAreFreeOfCycles(FIXTURE_LAYOUT)).stream()
                        .map(LayeringArchTest::cycleFromItsFirstSlice)
                        .toList())
                .as("every cycle between fixture modules, the map-allowed feature cycle among them")
                .containsExactlyInAnyOrder(
                        "inventory -> orders -> inventory",
                        "db -> platform -> db",
                        "db -> greeting -> db",
                        "greeting -> platform -> greeting",
                        "db -> greeting -> platform -> db",
                        "db -> platform -> greeting -> db");
    }

    /**
     * {@code PartnerPlatformCallsGreeting} calls {@code greeting}'s internal {@code GreetingService}, and {@code
     * PartnerPlatformCallsGreetingApiSubpackage} a class in {@code greeting.api.dto}, a subpackage of the {@code api}
     * package and so not its surface, each over an edge the fixture map allows: both reported. Their feature's name
     * contains {@code platform}, so a filter matching by substring would drop them. {@code BillingCallsGreeting} and
     * {@code FeedbackCallsGreeting} reach only {@code greeting.api}, and the {@code orders}–{@code inventory} pair
     * only each other's {@code api}: not reported.
     */
    @Test
    void theApiRuleReportsOnlyAReferenceIntoAnotherFeaturesInternals() {
        assertThat(details(featuresReachAnotherFeatureOnlyThroughItsApi(FIXTURE_LAYOUT)))
                .as("only the references past greeting's api package itself are reported")
                .anySatisfy(detail -> assertThat(detail)
                        .startsWith("Method <" + FIXTURE_BASE + ".partnerplatform.PartnerPlatformCallsGreeting.")
                        .contains("<" + FIXTURE_BASE + ".greeting.GreetingService"))
                .anySatisfy(detail -> assertThat(detail)
                        .startsWith("Method <" + FIXTURE_BASE
                                + ".partnerplatform.PartnerPlatformCallsGreetingApiSubpackage.")
                        .contains("<" + FIXTURE_BASE + ".greeting.api.dto.GreetingView"))
                .allSatisfy(detail -> assertThat(detail)
                        .matches("(?s)Method <\\Q" + FIXTURE_BASE + ".partnerplatform.\\E"
                                + "(PartnerPlatformCallsGreeting|PartnerPlatformCallsGreetingApiSubpackage)\\..*")
                        .doesNotContain("<" + FIXTURE_BASE + ".greeting.api.GreetingApi"));
    }

    /**
     * {@code FeedbackCallsGreeting} reaches {@code greeting} only through its {@code api} package, and the fixture
     * map has no {@code feedback -> greeting} line: reported. Its feature's name contains {@code db}, so a filter
     * matching by substring would drop it. The map lines {@code greeting -> billing}, which no dependency takes, and
     * {@code ghost -> greeting}, which names no feature, are reported. Every allowed edge the code takes, {@code
     * billing}'s and the {@code orders}–{@code inventory} pair included, is not.
     */
    @Test
    void theMapRuleReportsAnEdgeTheMapLacksAndALineNoCodeTakes() {
        assertThat(details(featureDependenciesAreInTheAllowedMap(FIXTURE_LAYOUT)))
                .as("the edge with no map line and the two map lines no edge takes, nothing else")
                .anySatisfy(detail -> assertThat(detail)
                        .startsWith("Method <" + FIXTURE_BASE + ".feedback.FeedbackCallsGreeting.")
                        .contains("<" + FIXTURE_BASE + ".greeting.api.GreetingApi"))
                .anySatisfy(detail -> assertThat(detail)
                        .isEqualTo("Allowed feature map line <greeting -> billing> is taken by no dependency; a line"
                                + " goes in with the code that takes it and out with the last code that did"))
                .anySatisfy(
                        detail -> assertThat(detail)
                                .startsWith(
                                        "Allowed feature map line <ghost -> greeting> names [ghost], not among the feature packages"))
                .allSatisfy(detail -> assertThat(detail)
                        .matches("(?s)(Method <\\Q" + FIXTURE_BASE + ".feedback.FeedbackCallsGreeting.\\E"
                                + "|Allowed feature map line <(greeting -> billing|ghost -> greeting)> ).*"));
    }

    /**
     * Each map below is malformed and must throw when parsed, which fails every test that builds the map rule: a
     * line without the spaced arrow, with a third name, with a capital, a comment, a line naming the platform tier
     * or the generated tree, a feature mapped to itself, and a repeated line. Blank lines and surrounding spaces are
     * not malformed.
     */
    @Test
    void theMapRefusesEveryLineThatIsNotOneEdgeBetweenTwoFeatures() {
        for (String map : List.of(
                "orders->inventory",
                "orders -> inventory -> billing",
                "Orders -> inventory",
                "# orders -> inventory",
                "orders -> platform",
                "db -> orders",
                "orders -> orders",
                "orders -> inventory\norders -> inventory")) {
            assertThatThrownBy(() -> new Layout(FIXTURE_BASE, map).allowedEdges())
                    .as(map)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(new Layout(FIXTURE_BASE, "\n   orders -> inventory  \n\n").allowedEdges())
                .containsExactly(new FeatureEdge("orders", "inventory"));
    }

    /**
     * {@code PlatformCallsFeature} reaches into {@code greeting} and {@code PlatformCallsTheBasePackage} into a
     * class directly in the base package; {@code PlatformReadsGeneratedTree} reads {@code db}, which is allowed.
     */
    @Test
    void thePlatformRuleReportsOnlyItsCallsIntoAFeatureAndTheBasePackage() {
        List<String> details = details(platformDependsOnNoFeature(FIXTURE_LAYOUT));
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
        List<String> details = details(generatedTreeDependsOnNothingOutsideIt(FIXTURE_LAYOUT));
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
     * {@code RootCallsFeature}, directly in the fixture base package, calls feature {@code greeting}: reported.
     * {@code RootWiring} and {@code RootController}, beside it, depend on no feature: not reported.
     */
    @Test
    void theBasePackageRuleReportsOnlyItsReferenceIntoAFeature() {
        assertThat(details(basePackageDependsOnNoFeature(FIXTURE_LAYOUT)))
                .as("only the base-package class that depends on a feature is reported")
                .isNotEmpty()
                .allSatisfy(detail -> assertThat(detail)
                        .startsWith("Method <" + FIXTURE_BASE + ".RootCallsFeature.")
                        .contains("<" + FIXTURE_BASE + ".greeting.GreetingService"));
    }

    /**
     * The {@code @RestController} in the fixture platform tier and the {@code @Controller} directly in the fixture
     * base package are reported; the one in fixture feature {@code greeting} is not.
     */
    @Test
    void theControllerRuleReportsEveryControllerOutsideAFeature() {
        assertThat(details(controllersLiveInFeaturePackages(FIXTURE_LAYOUT)))
                .as("only the controllers outside every feature package are reported")
                .satisfiesExactlyInAnyOrder(
                        detail -> assertThat(detail).startsWith("Class <" + FIXTURE_BASE + ".RootController> "),
                        detail -> assertThat(detail)
                                .startsWith("Class <" + FIXTURE_BASE + ".platform.PlatformController> "));
    }

    /**
     * Every method of this class that returns an {@link ArchRule} or a subtype of it, such as the {@code SliceRule}
     * a slices rule builds, so a narrower declared return type cannot drop a rule from either check. Each must be a
     * {@code static} factory taking a {@link Layout}, so one that is not fails here instead of being skipped.
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
                .as("every method returning an ArchRule is a static factory taking a Layout")
                .allSatisfy(factory -> assertThat(Modifier.isStatic(factory.getModifiers())
                                && Arrays.equals(factory.getParameterTypes(), new Class<?>[] {Layout.class}))
                        .as(factory.getName() + " is a static factory taking a Layout")
                        .isTrue());
        return factories;
    }

    private static List<String> details(ArchRule rule) {
        return rule.evaluate(FIXTURES).getFailureReport().getDetails();
    }

    /**
     * A cycle report's slice names, read from its header ({@code Cycle detected: Slice a -> Slice b -> Slice a},
     * one slice a line, before the first numbered dependency list) and rotated to start at the alphabetically first.
     */
    private static String cycleFromItsFirstSlice(String detail) {
        String header = detail.lines()
                .takeWhile(line -> !line.strip().matches("\\d+\\. .*"))
                .collect(Collectors.joining(" "));
        List<String> slices = Pattern.compile("Slice (\\w+)")
                .matcher(header)
                .results()
                .map(match -> match.group(1))
                .toList();
        List<String> open = slices.subList(0, slices.size() - 1);
        int first = open.indexOf(open.stream().min(Comparator.naturalOrder()).orElse(""));
        List<String> rotated = new ArrayList<>(open.subList(first, open.size()));
        rotated.addAll(open.subList(0, first));
        rotated.add(rotated.get(0));
        return String.join(" -> ", rotated);
    }
}
