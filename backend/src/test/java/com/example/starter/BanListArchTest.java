package com.example.starter;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.jooq.DSLContext;
import org.jooq.UpdatableRecord;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

/**
 * The ban list, enforced at the bytecode level over the main code. Tests are excluded: they
 * legitimately use {@code @Autowired} fields and wall-clock time.
 *
 * <p>Every rule is a {@code static final ArchRule} field with one {@code @Test} method that checks it. Two
 * meta-tests keep this class honest: {@link BanCoverageMetaTest} reconciles the declared ban list against the
 * test methods here in both directions, and {@link BanListNegativeControlTest} evaluates every rule field over
 * the committed violating fixtures and fails if any rule finds nothing, because an ArchUnit rule pointed at
 * the wrong package or with its empty-should guard disabled passes silently.
 *
 * <p>Bans that need source or AST context are declared DEFERRED in {@link BanCoverageMetaTest}.
 */
class BanListArchTest {

    static final String BASE = "com.example.starter";
    static final String PLATFORM = BASE + ".platform..";
    static final String GENERATED = BASE + ".db..";
    static final String OBSERVABILITY = BASE + ".platform.observability..";
    static final String KEYSET_PAGER = BASE + ".platform.KeysetPager";

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);

    private static final Set<String> ATTACHED_RECORD_CRUD =
            Set.of("store", "insert", "update", "delete", "merge", "refresh", "changed", "touched", "modified");

    private static final Set<String> BIG_DECIMAL_ARITHMETIC = Set.of(
            "add",
            "subtract",
            "multiply",
            "divide",
            "remainder",
            "negate",
            "abs",
            "pow",
            "movePointLeft",
            "movePointRight",
            "scaleByPowerOfTen",
            "divideToIntegralValue",
            "divideAndRemainder",
            "sqrt");

    private static final Set<String> PLAIN_SQL_FACTORIES = Set.of(
            "field",
            "condition",
            "table",
            "resultQuery",
            "sql",
            "query",
            "execute",
            "fetch",
            "fetchSingle",
            "fetchOptional",
            "fetchValue",
            "fetchMany",
            "fetchLazy");

    private static final Set<String> POOLED_EXECUTOR_FACTORIES = Set.of(
            "newFixedThreadPool",
            "newCachedThreadPool",
            "newSingleThreadExecutor",
            "newWorkStealingPool",
            "newScheduledThreadPool",
            "newSingleThreadScheduledExecutor");

    private static final List<String> RUNTIME_SILENT_ANNOTATIONS = List.of(
            "org.springframework.transaction.annotation.Transactional",
            "org.springframework.scheduling.annotation.Async",
            "org.springframework.scheduling.annotation.Scheduled",
            "org.springframework.cache.annotation.Cacheable",
            "org.springframework.cache.annotation.CachePut",
            "org.springframework.cache.annotation.CacheEvict",
            "org.springframework.cache.annotation.Caching",
            "org.springframework.context.annotation.Lazy",
            "io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker",
            "io.github.resilience4j.bulkhead.annotation.Bulkhead",
            "io.github.resilience4j.retry.annotation.Retry");

    static final ArchRule CONSTRUCTOR_INJECTION_ONLY = CompositeArchRule.of(noFields()
                    .should()
                    .beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                    .orShould()
                    .beAnnotatedWith("jakarta.inject.Inject")
                    .orShould()
                    .beAnnotatedWith("jakarta.annotation.Resource"))
            .and(noMethods().should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired"))
            .because(
                    "constructor injection only; a field or setter dependency is absent from the constructor signature");

    /** Annotated or meta-annotated, on classes and on methods: a repo-defined wrapper annotation is the same ban. */
    static final ArchRule NO_RUNTIME_SILENT_ANNOTATIONS = runtimeSilentAnnotationRule();

    static final ArchRule NO_WALL_CLOCK_READS = noClasses()
            .should()
            .callMethod(System.class, "currentTimeMillis")
            .orShould()
            .callMethod(System.class, "nanoTime")
            .orShould()
            .callMethod(Instant.class, "now")
            .orShould()
            .callMethod(LocalDate.class, "now")
            .orShould()
            .callMethod(LocalDateTime.class, "now")
            .orShould()
            .callMethod(ZonedDateTime.class, "now")
            .orShould()
            .callMethod(OffsetDateTime.class, "now")
            .orShould()
            .callConstructor(Date.class)
            .because("inject java.time.Clock; the zero-argument now() methods read the machine clock silently");

    static final ArchRule NO_RANDOM_UUIDS = noClasses()
            .should()
            .callMethod(UUID.class, "randomUUID")
            .because("ids are UUIDv7 via Ids.newId(); a random v4 key scatters the primary-key index");

    static final ArchRule DSLCONTEXT_NEVER_INJECTED = noFields()
            .should()
            .haveRawType("org.jooq.DSLContext")
            .because(
                    "DSLContext arrives only as the tx.* lambda parameter, so SQL outside a transaction is unwritable");

    static final ArchRule NO_SILENT_JOOQ_FETCHERS = noClasses()
            .should()
            .callMethodWhere(target(name("fetchOne")))
            .orShould()
            .callMethodWhere(target(name("fetchAny")))
            .because(
                    "fetchOne/fetchAny silently return null or an arbitrary row; use fetchOptional/fetchSingle/fetchExists");

    static final ArchRule NO_REACTIVE_TYPES = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("reactor.core.publisher..", "org.springframework.web.reactive..")
            .because("Spring MVC only; the reactive paradigm is banned");

    static final ArchRule NO_JPA_SPRING_DATA_OR_JDBCTEMPLATE = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "jakarta.persistence..",
                    "org.hibernate..",
                    "org.springframework.data..",
                    "org.springframework.orm..")
            .orShould()
            .dependOnClassesThat()
            .haveNameMatching(
                    "org\\.springframework\\.jdbc\\.core\\..*(JdbcTemplate|JdbcClient|SimpleJdbcInsert|SimpleJdbcCall)")
            .because(
                    "persistence is jOOQ through tx.*; JPA, Hibernate, Spring Data and the JdbcTemplate family are banned. "
                            + "spring-jdbc itself stays because the jOOQ path uses its DataSource plumbing, so this is a type ban");

    static final ArchRule NO_ATTACHED_RECORD_CRUD = noClasses()
            .should()
            .callMethodWhere(
                    new DescribedPredicate<JavaMethodCall>("call attached-record CRUD on a jOOQ UpdatableRecord") {
                        @Override
                        public boolean test(JavaMethodCall call) {
                            return ATTACHED_RECORD_CRUD.contains(call.getName())
                                    && call.getTarget().getOwner().isAssignableTo(UpdatableRecord.class);
                        }
                    })
            .because("writes are explicit tx.write plus the DSL; an attached record persists itself silently");

    static final ArchRule NO_RAW_BIGDECIMAL_ARITHMETIC_OUTSIDE_PLATFORM = noClasses()
            .that()
            .resideOutsideOfPackage(PLATFORM)
            .should()
            .callMethodWhere(new DescribedPredicate<JavaMethodCall>("perform raw BigDecimal arithmetic") {
                @Override
                public boolean test(JavaMethodCall call) {
                    return BIG_DECIMAL_ARITHMETIC.contains(call.getName())
                            && call.getTarget().getOwner().isEquivalentTo(BigDecimal.class);
                }
            })
            .because(
                    "amounts use the Money value object; raw BigDecimal arithmetic is banned outside the platform tier");

    static final ArchRule NO_PLAIN_SQL_STRINGS = noClasses()
            .that()
            .resideOutsideOfPackage(GENERATED)
            .should()
            .callMethodWhere(new DescribedPredicate<JavaMethodCall>("construct plain SQL from a String via jOOQ") {
                @Override
                public boolean test(JavaMethodCall call) {
                    JavaClass owner = call.getTarget().getOwner();
                    boolean plainSqlOwner = owner.isEquivalentTo(DSL.class) || owner.isAssignableTo(DSLContext.class);
                    if (!plainSqlOwner || !PLAIN_SQL_FACTORIES.contains(call.getName())) {
                        return false;
                    }
                    List<JavaClass> params = call.getTarget().getRawParameterTypes();
                    return !params.isEmpty() && params.get(0).isEquivalentTo(String.class);
                }
            })
            .because("SQL is compile-checked against the schema through generated jOOQ; a SQL string is not. "
                    + "A project needing a raw-SQL carve-out names one package here and pins it with a test");

    /** Offset pagination in every jOOQ spelling, banned everywhere but the one owned pager. */
    static final ArchRule NO_OFFSET_PAGINATION = noClasses()
            .that()
            .doNotHaveFullyQualifiedName(KEYSET_PAGER)
            .should()
            .callMethodWhere(new DescribedPredicate<JavaMethodCall>("paginate by offset through jOOQ") {
                @Override
                public boolean test(JavaMethodCall call) {
                    if (!call.getTarget().getOwner().getPackageName().startsWith("org.jooq")) {
                        return false;
                    }
                    String name = call.getName();
                    int arity = call.getTarget().getRawParameterTypes().size();
                    return name.equals("offset")
                            || name.equals("addOffset")
                            || ((name.equals("limit") || name.equals("addLimit")) && arity == 2);
                }
            })
            .because("keyset pagination only, through KeysetPager; offset paging skips and repeats rows under writes");

    static final ArchRule NO_POOLED_EXECUTORS = noClasses()
            .should()
            .callMethodWhere(new DescribedPredicate<JavaMethodCall>("create a pooled platform-thread executor") {
                @Override
                public boolean test(JavaMethodCall call) {
                    return POOLED_EXECUTOR_FACTORIES.contains(call.getName())
                            && call.getTarget().getOwner().isEquivalentTo(Executors.class);
                }
            })
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("java.util.concurrent.ThreadPoolExecutor")
            .because(
                    "one virtual thread per task, never pooled; Executors.newVirtualThreadPerTaskExecutor() or Thread.startVirtualThread");

    static final ArchRule NO_PATCH = noMethods()
            .should()
            .beAnnotatedWith("org.springframework.web.bind.annotation.PatchMapping")
            .because(
                    "PATCH is banned on every endpoint; updates are a full-replace PUT under an If-Match precondition");

    static final ArchRule NO_RAW_LOGGING_OUTSIDE_FACADE = noClasses()
            .that()
            .resideOutsideOfPackage(OBSERVABILITY)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.slf4j..", "java.util.logging..", "org.apache.logging.log4j..", "ch.qos.logback..")
            .orShould()
            .accessField(System.class, "out")
            .orShould()
            .accessField(System.class, "err")
            .orShould()
            .callMethodWhere(target(name("printStackTrace")))
            .because(
                    "logging goes through the typed facade so the mandatory fields and the event catalog cannot be bypassed");

    static final ArchRule NO_LOMBOK_OR_MAPSTRUCT = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("lombok..", "org.mapstruct..")
            .because(
                    "records and explicit mappers; generated accessors and mappers are behaviour absent from the text");

    private static ArchRule runtimeSilentAnnotationRule() {
        ArchRule rule = null;
        for (String annotation : RUNTIME_SILENT_ANNOTATIONS) {
            ArchRule onClasses =
                    noClasses().should().beAnnotatedWith(annotation).orShould().beMetaAnnotatedWith(annotation);
            ArchRule onMethods =
                    noMethods().should().beAnnotatedWith(annotation).orShould().beMetaAnnotatedWith(annotation);
            rule = rule == null
                    ? CompositeArchRule.of(onClasses).and(onMethods)
                    : ((CompositeArchRule) rule).and(onClasses).and(onMethods);
        }
        return Objects.requireNonNull(rule)
                .because(
                        "runtime-silent magic is banned: behaviour absent from the program text is invisible to a maintainer who "
                                + "only reads text. Explicit tx.*, an explicit scheduler, an explicit cache, an explicit fallback");
    }

    @Test
    void importsMainCode() {
        assertThat(MAIN.size()).isGreaterThanOrEqualTo(10);
        assertThat(MAIN.stream().map(JavaClass::getName))
                .contains(BASE + ".platform.Money", BASE + ".platform.Tx", BASE + ".Application");
    }

    @Test
    void constructorInjectionOnly() {
        CONSTRUCTOR_INJECTION_ONLY.check(MAIN);
    }

    @Test
    void noRuntimeSilentAnnotations() {
        NO_RUNTIME_SILENT_ANNOTATIONS.check(MAIN);
    }

    @Test
    void noWallClockReadsInMainCode() {
        NO_WALL_CLOCK_READS.check(MAIN);
    }

    @Test
    void noRandomUuids() {
        NO_RANDOM_UUIDS.check(MAIN);
    }

    @Test
    void dslContextIsNeverInjected() {
        DSLCONTEXT_NEVER_INJECTED.check(MAIN);
    }

    @Test
    void noSilentJooqFetchers() {
        NO_SILENT_JOOQ_FETCHERS.check(MAIN);
    }

    @Test
    void noReactiveOrWebFluxTypes() {
        NO_REACTIVE_TYPES.check(MAIN);
    }

    @Test
    void noJpaSpringDataOrJdbcTemplate() {
        NO_JPA_SPRING_DATA_OR_JDBCTEMPLATE.check(MAIN);
    }

    @Test
    void noAttachedRecordCrud() {
        NO_ATTACHED_RECORD_CRUD.check(MAIN);
    }

    @Test
    void noRawBigDecimalArithmeticOutsidePlatform() {
        NO_RAW_BIGDECIMAL_ARITHMETIC_OUTSIDE_PLATFORM.check(MAIN);
    }

    @Test
    void noPlainSqlStrings() {
        NO_PLAIN_SQL_STRINGS.check(MAIN);
    }

    @Test
    void noOffsetPagination() {
        NO_OFFSET_PAGINATION.check(MAIN);
    }

    @Test
    void noPooledExecutors() {
        NO_POOLED_EXECUTORS.check(MAIN);
    }

    @Test
    void noPatchEndpoints() {
        NO_PATCH.check(MAIN);
    }

    @Test
    void noRawLoggingOutsideObservabilityFacade() {
        NO_RAW_LOGGING_OUTSIDE_FACADE.check(MAIN);
    }

    @Test
    void noLombokOrMapStruct() {
        NO_LOMBOK_OR_MAPSTRUCT.check(MAIN);
    }
}
