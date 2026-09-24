# Constitution

<!-- Pre-filled by the java-backend-template. spec-kit's `specify init --here` seeds this file only when it is
     missing, so it survives initialisation. The template is scaffolded before spec-kit's first command.
     Articles I–VI stay as they are: they restate what the template already enforces and are not open for
     re-planning per feature. Article VII is optional and starts empty; running `/speckit.constitution` is
     not a step of starting the project. -->

## Article I. The platform is decided

Java 25, Spring Boot Web MVC, jOOQ over PostgreSQL 18, Flyway, Jackson, Maven. Exact version pins. These are
not re-planned per feature; a plan's Technical Context inherits them. Changing one is its own change, with
the reason and the date recorded in `pom.xml`.

## Article II. The gates are the review

Code here is written by agents and read line by line by nobody. `mvn verify` in `backend/` is the definition of done and
every check in it fails the build on its own: the compile wall (Error Prone, NullAway, JSpecify), the
formatter, the executable ban list with its coverage and negative-control meta-tests, the layering test,
the migration lint, the error-catalog and OpenAPI snapshots, the integration suite against a real
PostgreSQL, the coverage floor, the licence allowlist. `docs/GATES.md` maps each gate to the skill directive
it implements and names what no gate reaches.

## Article III. Explicit over silent

Transactions are visible `tx.*` blocks. Dependencies arrive through constructors. Time comes from the
injected `Clock`. Ids are UUIDv7 from one producer. Money is the `Money` type and every rounding names its
mode. Logging goes through one typed facade with a catalogued event name. Anything that would make
behaviour happen outside the program text (`@Transactional`, `@Scheduled`, `@Cacheable`, a Java agent, an
attached jOOQ record, a `ThreadLocal` cache) is banned and the ban is a test.

## Article IV. The contract is a committed document

One OpenAPI document per major version, generated from the code, normalized by the repo's own normalizer,
diffed on every build. Every error is an RFC 9457 problem with a machine `code` from a compile-checked
catalog that is itself snapshotted. Pagination is keyset only. `PATCH` does not exist.

An identifier travels in the path only: no request body carries a member named after a path variable, not
even an optional echo. Each operation binds its own request type, and an update body declares only the
fields that operation writes. A requirement that an attempt to change X is refused is met by X's absence
from the update body, which the strict body reader refuses (`validation.identifier-in-path`,
`validation.unknown-field`); never by declaring X and comparing it, and never with a `*-immutable` code.

## Article V. One repo, one service, one micro-frontend

The service lives under `backend/` and is API-only. The micro-frontend lives under `frontend/`, builds to
static assets and deploys separately; the service never serves it. The two share one contract,
`backend/openapi/v1.json`, and one CI, whose `backend` and `frontend` jobs are both required on `main`.
The frontend's mechanism for being loaded by a shell is decided when a shell exists, at one named
exposure point, and nowhere else.

## Article VI. Features are packages

A feature is one package under the base package, depending on the platform tier and never on another
feature. It owns its migrations, its error catalog, its service, its controller and its integration test.
One feature package is the worked shape at any time and `backend/CLAUDE.md` names it; copy its shape. The
template's sample package is deleted by the first real feature and is never the shape again.

## Article VII. Project-specific articles

None. Empty is a complete state for this section: no command, gate or test reads whether it is filled, and
nothing is owed here. A project article is added only by amendment, as Governance says, when a rule turns out
to bind more than one feature and names the test or gate that holds it.

## Governance

Amendments are commits to this file with the reason in the message. A gate is removed only together with
the directive it implemented being retired in the skills repo, never because it is inconvenient.
