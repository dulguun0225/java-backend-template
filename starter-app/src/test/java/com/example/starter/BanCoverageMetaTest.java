package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The ban-to-rule completeness gate. Every ban is declared here with a {@link Tier}, and the declaration is
 * asserted: an {@link Tier#ARCHUNIT} ban must name a {@code @Test} that exists in {@link BanListArchTest}
 * (reflected, both directions), and a {@link Tier#DEFERRED} ban must carry a rationale. So a deferred ban can
 * never be described as enforced, and a newly wired rule can never be left undeclared.
 */
class BanCoverageMetaTest {

    enum Tier {
        ARCHUNIT,
        DEFERRED
    }

    record BanGate(Tier tier, String ref) {}

    /** Test methods in {@link BanListArchTest} that are guards, not ban rules. */
    private static final Set<String> NON_BAN_TESTS = Set.of("importsBothModulesMainCode");

    private static final Map<Ban, BanGate> GATES = new EnumMap<>(Ban.class);

    enum Ban {
        FIELD_OR_SETTER_INJECTION,
        RUNTIME_SILENT_ANNOTATIONS,
        WALL_CLOCK_READS,
        RANDOM_UUIDS,
        INJECTABLE_DSLCONTEXT,
        SILENT_JOOQ_FETCHERS,
        REACTIVE_WEBFLUX,
        JPA_SPRING_DATA_JDBCTEMPLATE,
        OFFSET_PAGINATION,
        POOLED_EXECUTORS,
        PATCH_ENDPOINTS,
        ATTACHED_RECORD_CRUD,
        RAW_BIGDECIMAL_ARITHMETIC,
        PLAIN_SQL_STRINGS,
        RAW_LOGGING_OUTSIDE_FACADE,
        LOMBOK_MAPSTRUCT,
        DOUBLE_FLOAT_FOR_MONEY,
        ORDER_BY_ID_COLUMN,
        INLINE_WIRE_CODE,
        SETSCALE_WITHOUT_POLICY
    }

    static {
        GATES.put(Ban.FIELD_OR_SETTER_INJECTION, new BanGate(Tier.ARCHUNIT, "constructorInjectionOnly"));
        GATES.put(Ban.RUNTIME_SILENT_ANNOTATIONS, new BanGate(Tier.ARCHUNIT, "noRuntimeSilentAnnotations"));
        GATES.put(Ban.WALL_CLOCK_READS, new BanGate(Tier.ARCHUNIT, "noWallClockReadsInMainCode"));
        GATES.put(Ban.RANDOM_UUIDS, new BanGate(Tier.ARCHUNIT, "noRandomUuids"));
        GATES.put(Ban.INJECTABLE_DSLCONTEXT, new BanGate(Tier.ARCHUNIT, "dslContextIsNeverInjected"));
        GATES.put(Ban.SILENT_JOOQ_FETCHERS, new BanGate(Tier.ARCHUNIT, "noSilentJooqFetchers"));
        GATES.put(Ban.REACTIVE_WEBFLUX, new BanGate(Tier.ARCHUNIT, "noReactiveOrWebFluxTypes"));
        GATES.put(Ban.JPA_SPRING_DATA_JDBCTEMPLATE, new BanGate(Tier.ARCHUNIT, "noJpaSpringDataOrJdbcTemplate"));
        GATES.put(Ban.OFFSET_PAGINATION, new BanGate(Tier.ARCHUNIT, "noOffsetPagination"));
        GATES.put(Ban.POOLED_EXECUTORS, new BanGate(Tier.ARCHUNIT, "noPooledExecutors"));
        GATES.put(Ban.PATCH_ENDPOINTS, new BanGate(Tier.ARCHUNIT, "noPatchEndpoints"));
        GATES.put(Ban.ATTACHED_RECORD_CRUD, new BanGate(Tier.ARCHUNIT, "noAttachedRecordCrud"));
        GATES.put(
                Ban.RAW_BIGDECIMAL_ARITHMETIC, new BanGate(Tier.ARCHUNIT, "noRawBigDecimalArithmeticOutsidePlatform"));
        GATES.put(Ban.PLAIN_SQL_STRINGS, new BanGate(Tier.ARCHUNIT, "noPlainSqlStrings"));
        GATES.put(Ban.RAW_LOGGING_OUTSIDE_FACADE, new BanGate(Tier.ARCHUNIT, "noRawLoggingOutsideObservabilityFacade"));
        GATES.put(Ban.LOMBOK_MAPSTRUCT, new BanGate(Tier.ARCHUNIT, "noLombokOrMapStruct"));
        GATES.put(
                Ban.DOUBLE_FLOAT_FOR_MONEY,
                new BanGate(
                        Tier.DEFERRED,
                        "needs a type scan of fields and signatures; convention today, Money carries every amount"));
        GATES.put(
                Ban.ORDER_BY_ID_COLUMN,
                new BanGate(
                        Tier.DEFERRED, "needs a query AST; convention today, KeysetPager owns the only id tiebreak"));
        GATES.put(
                Ban.INLINE_WIRE_CODE,
                new BanGate(
                        Tier.DEFERRED,
                        "needs a literal-shape AST check; convention today, ErrorCatalogSnapshotTest pins the catalogs but not call sites"));
        GATES.put(
                Ban.SETSCALE_WITHOUT_POLICY,
                new BanGate(
                        Tier.DEFERRED,
                        "BigDecimal.setScale(int, RoundingMode) outside Money needs argument context; convention today, RoundingPolicy names every mode"));
    }

    @Test
    void everyBanHasExactlyOneGate() {
        assertThat(GATES.keySet()).isEqualTo(EnumSet.allOf(Ban.class));
    }

    @Test
    void archunitAndDeferredBansAreBothNonEmpty() {
        assertThat(bansAt(Tier.ARCHUNIT)).isNotEmpty();
        assertThat(bansAt(Tier.DEFERRED)).isNotEmpty();
    }

    @Test
    void archunitBansReconcileExactlyWithBanListArchTest() {
        List<String> declaredRefList = GATES.values().stream()
                .filter(g -> g.tier() == Tier.ARCHUNIT)
                .map(BanGate::ref)
                .toList();
        assertThat(declaredRefList)
                .as("each ARCHUNIT ban must name a distinct @Test")
                .doesNotHaveDuplicates();
        Set<String> declaredRefs = new TreeSet<>(declaredRefList);
        Set<String> actualBanTests = Arrays.stream(BanListArchTest.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Test.class))
                .map(Method::getName)
                .filter(name -> !NON_BAN_TESTS.contains(name))
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(actualBanTests)
                .as("reflection found no ban rules in BanListArchTest")
                .isNotEmpty();
        assertThat(declaredRefs)
                .as(
                        "the ARCHUNIT refs must equal the @Test ban rules in BanListArchTest: a ban cannot claim a test that "
                                + "does not exist, and a rule cannot exist without a declared Ban")
                .isEqualTo(actualBanTests);
    }

    @Test
    void everyDeferredBanCarriesARationale() {
        GATES.forEach((ban, gate) -> {
            if (gate.tier() == Tier.DEFERRED) {
                assertThat(gate.ref())
                        .as("DEFERRED ban " + ban + " must carry a rationale")
                        .isNotBlank();
            }
        });
    }

    private static Set<Ban> bansAt(Tier tier) {
        return GATES.entrySet().stream()
                .filter(e -> e.getValue().tier() == tier)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Ban.class)));
    }
}
