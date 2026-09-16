# CLAUDE.md

This directory is a Java backend service created from `dulguun0225/java-backend-template`. Read this file,
then `docs/GATES.md`. If this is `backend/` inside a project, the project's own `CLAUDE.md` one level up
covers the repo shape; this one covers the service.

## What is already decided

The stack and every build gate are fixed by the template and by the skills that govern this code. Do not
re-derive or re-design them; do not write scaffolding, lint config, architecture tests or CI from scratch.
They exist, they are green, and `mvn verify` here is the definition of done.

- Java 25, Spring Boot Web MVC, jOOQ against PostgreSQL 18, Flyway, Jackson, Maven. Exact pins, moved by Renovate PRs.
- Persistence goes through `Tx` (`tx.read` / `tx.write`) over generated jOOQ. No JPA, no Spring Data, no `JdbcTemplate`, no `@Transactional`.
- Errors are RFC 9457 problems whose `code` comes from a `*ErrorCode` enum; the edge is `ApiExceptionHandler`.
- Money is the `Money` value object; every rounding names its `RoundingMode` through `RoundingPolicy`.
- Ids are UUIDv7 via `Ids.newId()`; time comes from the injected `Clock`; logging goes through `Log`.
- An `UPDATE` on a table carrying a `version` column goes through `VersionedUpdate`: one statement, the increment and the two-predicate guard, zero affected rows classified as stale or absent. Every other spelling is unwritable.
- One Maven module. The `platform` package is the foundation (no HTTP, depends on no feature); one feature = one package beside it, shaped like `greeting`. Copy its shape, then delete `greeting` and name the new package in this bullet: this line is the pointer the constitution's Article VI refers to, so the constitution itself never names a package.
- API-only. A frontend, if the project has one, is a separate static deploy and consumes `openapi/v1.json`.

## Skills

Install the rule set these gates implement, once per machine:

```
npx skills add dulguun0225/skills -a claude-code -y
```

The skills carry the reasoning and the checks; this directory carries the wired checks. When a skill and a
gate here disagree, the gate is wrong or stale: fix the gate, do not bypass it.

## Working here

- `node scripts/wall.mjs` is exactly what CI runs: forbidden flags, squawk, `mvn verify`, the jOOQ regenerate-twice drift check, the OpenAPI rerun under another timezone, the vacuum ruleset over the committed document, the vulnerability scan. Docker required. Scripts are Node, standard library only; `mise install` gives the pinned Node.
- `mvn spotless:apply` formats. `mvn -Pcodegen generate-sources` regenerates jOOQ after a migration; it runs before compile, so it works while main code still references a table that does not exist yet.
- A new wire error code goes in a catalog enum and in `ErrorCatalogSnapshotTest`'s list; the build tells you when the snapshot needs updating.
- A new table needs an owner row in `TableOwnershipTest.OWNERS`; the build fails until it has one.
- A new endpoint changes `openapi/v1.json`; the build writes the actual document to `target/` and tells you to review and copy it.
- A new ban needs three things: the rule in `BanListArchTest`, a fixture in `starterfixtures`, and a row in `BanCoverageMetaTest`. The build refuses any two without the third.
- The coverage floor and the migration timeouts are this service's call; change them in one commit that says why.
