# Gates

Every check `mvn verify` and `.github/workflows/ci.yml` run, the skill directive it implements, and where it
lives. Then the directives the skills name that nothing here reaches, so a reader does not mistake a green
build for coverage of them. Skill names are the directories under `skills/` in `dulguun0225/skills`.

Legend for the *kind* column: **wall** fails compilation; **test** fails `mvn verify`; **ci** is a shell step
in the workflow that fails on exit code. Nothing is advisory.

## Wired

| Gate | Kind | Where | Implements |
|---|---|---|---|
| Error Prone + NullAway + JSpecify as compile errors; `EmptyCatch` and `FutureReturnValueIgnored` promoted to ERROR | wall | `pom.xml` compiler plugin | java-backend-rules *JSpecify, checked by NullAway*; money-java `M-5`; async-handoff-java `E-5` |
| Java pinned at 25, no dynamic versions, duplicate-version ban | test (Enforcer) | `pom.xml` enforcer | java-backend-rules *The pin is created at the newest supported LTS* |
| jqwik ceiling `< 1.10` | test (Enforcer) | `pom.xml` enforcer | llm-default-traps *The jqwik version pin* |
| Banned dependencies: JPA, Hibernate, Spring Data (every flavour), WebFlux, Lombok, MapStruct, non-Logback SLF4J bindings, `de.jollyday`, JSR-275, JScience, the OpenTelemetry agent | test (Enforcer) | `pom.xml` enforcer | java-backend-rules *jOOQ against PostgreSQL, with JPA and Spring Data banned*; java-backend-observability *The logging backend is pinned*; llm-default-traps *jollyday*, *JSR-385* |
| Formatter, fail on diff | test | `pom.xml` spotless | guardrails-toolchain *A guardrail is a tool whose verdict fails a build by itself* |
| Executable ban list, bytecode level, both modules, annotated and meta-annotated: field/setter injection; `@Transactional`, `@Async`, `@Scheduled`, `@Cacheable` family, `@Lazy`, Resilience4j annotations; wall-clock reads; `UUID.randomUUID`; injectable `DSLContext`; `fetchOne`/`fetchAny`; reactive types; JPA/Spring Data/`JdbcTemplate` family types; attached-record CRUD; raw `BigDecimal` arithmetic outside platform; plain-SQL strings; offset pagination outside `KeysetPager`; pooled executors; `@PatchMapping`; raw logging outside the facade; Lombok/MapStruct | test | `BanListArchTest` | java-backend-rules *The runtime-silent ban list*, *The ban list is an executable test class*, and the jOOQ directives; java-backend-api *Keyset pagination only*, *`PATCH` is banned*; java-backend-observability *One typed logging facade*; money-java `M-2`; caching-java `C-2` (and its named Resilience4j gap) |
| Ban-to-rule reconciliation, both directions; deferred bans carry a rationale | test | `BanCoverageMetaTest` | java-backend-rules *Every ban names the check that enforces it* |
| One violating fixture per ArchUnit rule, asserted | test | `BanListNegativeControlTest`, `starterfixtures` | async-handoff-java `E-25` (applies to every ArchUnit gate) |
| Layering: platform depends on no feature; features do not depend on each other; no controller in platform | test | `LayeringArchTest` | java-backend-rules package confinement (the named packages every predicate refers to) |
| `Settings.withAttachRecords(false)` stays false | test | `TxSettingsTest` | java-backend-rules *jOOQ's own runtime-silent CRUD is banned* |
| Committed config carries `spring.threads.virtual.enabled`, `keep-alive`, structured ECS console logging, a small fixed Hikari pool | test | `ConfigDefaultsTest` | java-backend-rules *Virtual threads are enabled by one property*, *Bound concurrency at the limited resource*; java-backend-observability *Structured JSON on stdout* |
| No `-javaagent`, no preview flag, in poms, jvm.config, Dockerfile, compose, workflows, scripts | test + ci | `ForbiddenFlagsTest`, `scripts/check-forbidden-flags.sh` | java-backend-observability *The weaving-agent ban*; java-backend-rules *No preview APIs* |
| Migration conventions: uuidv7 keys, no sequences or random generators, `timestamptz`, no clock defaults, money columns `numeric(19|20,4) not null` with NaN check and currency sibling, no float or `money` types; each rule proven on a negative fixture | test | `MigrationConventionsTest`, `migration-fixtures/` | primary-keys check lines; money-java `M-10`, `M-11`, `M-31`, `M-32`, `M-33`; java-backend-rules clock layer clause |
| squawk over changed migrations, default rule set, exit code | ci | `scripts/squawk-changed-migrations.sh`, `.squawk.toml` | java-backend-rules *Every migration is linted for lock and rewrite hazards*; money-java `M-42` |
| jOOQ classes regenerated from the committed migrations against a real PostgreSQL, twice, under a different timezone and locale, byte-identical to each other and to the committed tree | ci | `scripts/check-codegen-drift.sh`, `JooqCodegenRunner` | java-backend-rules *jOOQ classes are generated from the committed migrations*; guardrails-toolchain *Every committed generated artifact is byte-reproducible* |
| Error catalog snapshot; one wire code maps to one status; catalog list equals the classpath | test | `ErrorCatalogSnapshotTest`, `error-catalog-snapshot.txt` | java-backend-api *Every error carries a code from one compile-checked catalog*, *The catalog is snapshotted and diffed* |
| Every framework-edge error is a coded RFC 9457 problem; the 500 carries no message and its `incidentId` resolves to exactly one facade log event | test | `ApiErrorEdgeIT`, `ErrorLeakIT` | java-backend-api *Every error response is a problem document*, *One advice builds every error body*; java-backend-observability *The correlation id in an error response resolves to a log event* |
| Facade binds correlation, module, role, instance at emit time; catalog events at their level; degrades cleanly outside a request | test | `LogContractTest` | java-backend-observability *Every scoped log event carries the correlation fields*, *Event and metric names come from a compile-checked catalog* (events half) |
| OpenAPI 3.1 generated, normalized by the repo's normalizer, diffed against `openapi/v1.json`; CI reruns under another timezone and locale | test + ci | `OpenApiSnapshotIT`, `openapi/v1.json` | java-backend-api *One committed OpenAPI document*, *One hand-owned canonical normalizer*, *Authoritative generation runs on one operating system* |
| Money: exact, minor-unit scale, excess precision rejected, cross-currency fails, ratio scaling rounds once | test (jqwik) | `MoneyPropertiesTest`, `RoundingPolicyTest` | money-java `M-1`, `M-3`, `M-4`, `M-7`, `M-24` |
| UUIDv7 bit layout and ordering | test | `IdsTest` | primary-keys worked case |
| Integration tests against real PostgreSQL 18 via Testcontainers, never H2 | test | `TestcontainersConfiguration`, `*IT` | java-backend-rules *Integration tests run against real PostgreSQL* |
| Coverage floor, merged unit and integration, per module | test (JaCoCo) | `pom.xml` jacoco, `jacoco.line.minimum` | java-backend-rules *Coverage is gated by JaCoCo* (the ratio is this repo's call) |
| Licence allowlist, deny by default, unknown fails | test | `pom.xml` license plugin, `licenses/` | guardrails-toolchain *Licences gate deny-by-default over a committed dependency inventory* |
| SBOM (CycloneDX) and osv-scanner over it, pinned by checksum, exit code; suppression inventory committed | ci | `scripts/osv-scan.sh`, `osv-scanner.toml` | guardrails-toolchain *Gate on exit codes and committed artifacts*, *Record the caveat that bites* (suppression inventory) |
| Every action SHA-pinned | ci | `scripts/check-action-pins.sh` | llm-default-traps *CI actions and scanners are SHA-pinned* |
| Required status checks on the default branch equal the committed job names | ci | `scripts/check-required-checks.sh`, `.github/rulesets/main.json` | guardrails-toolchain layer clause on *fails the build* (the forge's settings are not a committed file) |
| Named path for moving a pin | process | `renovate.json` | llm-default-traps composite condition on SHA pins |

## Named gaps: directives with no gate here

Each is a directive the skills state that this template does not enforce. Some need a per-project decision
before they can be wired; some have no host; some are dormant until the repo does the thing. A row leaving
this list moves to the table above in the same commit.

| Directive | Why not here | What would wire it |
|---|---|---|
| java-backend-api *The committed document is the single conformance oracle* (Schemathesis) | Python tool, app booted in a container, pinned seed; not yet wired | a CI job running Schemathesis 4.x against the compose stack, `deterministic = true`, seed committed, retries off |
| java-backend-api vacuum lints: no offset/page parameter, no `PATCH` in the document, `limit` declares a maximum, every error response uses the problem schema, temporal naming matches format | rulesets not yet authored; the ArchUnit half of two of them is wired | `vacuum lint -r rulesets/openapi.yaml openapi/v1.json` in CI with the five rules |
| java-backend-api *Breaking-change diff* (oasdiff) | scoped to surfaces whose consumers are not rebuilt in the same PR; the template has none | `oasdiff breaking --fail-on ERR` against the last released document once a partner surface exists |
| java-backend-api *`limit` has a default and a hard maximum*, *Cursors are opaque, sealed*, *Strong ETags*, *The guarded version-column update* | no list endpoint, no versioned table in the worked example | tests beside the first list endpoint and the first versioned table; `KeysetPager` is the pager |
| java-backend-observability *Domain types are unloggable by type* | the facade's `LogField` makes a domain object unpassable, which is the type-level half; the Error Prone check the skill demands is not written | an Error Prone `BugChecker` over `Log` call sites |
| java-backend-observability *Metric label cardinality*, *Alert rules are committed code with a fire-test*, *Database facts are exported by one poller* | no metrics or alert rules in the template | Micrometer `MeterFilter` bound, `promtool test rules` job, when the first meter or rule lands |
| java-backend-rules *The fan-out helper*, *One virtual thread per task* (helper half) | no fan-out in the template; the pooled-executor ban is wired | the owned helper plus its context-capture test when the first fan-out is written |
| java-backend-rules *Keep the pinning event on, and alert on it* | deployment, not build | JFR `jdk.VirtualThreadPinned` and an alert in the deployment |
| money-java `M-6`, `M-8`, `M-23` (pitest on money packages), `M-35`, `M-36`, `M-37`, `M-38`, `M-39`, `M-40`, `M-41`, `M-43` | the template has no money-bearing feature; several need the feature to exist | wire beside the first money feature; pitest `>= 1.25.8` scoped to the money package |
| money-java `M-12`, `M-13`, `M-15`, `M-16` (string decimals, required money fields) | `StringDecimalDeserializer` and `MonetaryAmount` are shipped; the parse-rejection tests wait for the first money DTO | a deserialization test per money DTO |
| money-java `M-17`, `M-18` (Idempotency-Key, If-Match on money POSTs) | no money endpoint | the idempotency table and the version helper, with the tests the skill lists |
| caching-java, async-handoff-java (all) | dormant: no cache, no broker | the skills' wiring lists, when the first cache or handoff is introduced |
| guardrails-toolchain *Lockfile-exact install* | Maven has no first-class lockfile; exact pins plus the SBOM diff stand in | the `maven-lockfile` plugin with a `validate` goal, if its checksum recording is judged worth the dependency |
| guardrails-toolchain *Assign each defect class to the earliest layer*, *Run a completeness critic*, *A diff-scoped review cannot see erosion* | process artefacts a project writes, not build steps | a committed defect-class-to-layer map; a scheduled sweep with a canary per lens |
| java-backend-rules *Never fan out to database work while holding a connection* | stated by the skill as not statically detectable | none; review |
| Config-default assertions read the checked-in default only | an environment override in the deployed process is outside their reach | none in the build; a deployment probe |
| Authentication and authorization | not a subject of these skills; every endpoint here is open | the project's own decision, with a per-endpoint access marker and an ArchUnit rule requiring it |

## Per-project parameters

The skills refuse to supply these; the template ships a value only so the build is green, and each is a
one-commit change with a reason:

- `jacoco.line.minimum` in the poms
- the migration `lock_timeout` and `statement_timeout`
- `hikari.maximum-pool-size`
- money precision, `numeric(19,4)` or `numeric(20,4)`, when the first money column lands
- the licence allowlist
