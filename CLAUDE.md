# CLAUDE.md

This repo was created from `dulguun0225/java-backend-template`. Read this file, then `docs/GATES.md`.

## What is already decided

The stack and every build gate are fixed by the template and by the skills that govern this code. Do not
re-derive or re-design them; do not write scaffolding, lint config, architecture tests or CI from scratch.
They exist, they are green, and `mvn verify` is the definition of done.

- Java 25, Spring Boot Web MVC, jOOQ against PostgreSQL 18, Flyway, Jackson, Maven. Exact pins, moved by Renovate PRs.
- Persistence goes through `Tx` (`tx.read` / `tx.write`) over generated jOOQ. No JPA, no Spring Data, no `JdbcTemplate`, no `@Transactional`.
- Errors are RFC 9457 problems whose `code` comes from a `*ErrorCode` enum; the edge is `ApiExceptionHandler`.
- Money is the `Money` value object; every rounding names its `RoundingMode` through `RoundingPolicy`.
- Ids are UUIDv7 via `Ids.newId()`; time comes from the injected `Clock`; logging goes through `Log`.
- One Maven module. The `platform` package is the foundation (no HTTP, depends on no feature); one feature = one package beside it, shaped like `greeting`. Copy its shape, then delete `greeting`.

## Skills

Install the rule set these gates implement, once per machine:

```
npx skills add dulguun0225/skills -a claude-code -y
```

The skills carry the reasoning and the checks; this repo carries the wired checks. When a skill and a gate
here disagree, the gate is wrong or stale: fix the gate, do not bypass it.

## Working in this repo

- `mvn verify` runs the whole wall (Docker required for the integration tests). Nothing is advisory.
- `mvn spotless:apply` formats. `mvn -Pcodegen generate-sources` regenerates jOOQ after a migration; it runs before compile, so it works while main code still references a table that does not exist yet.
- A new wire error code goes in a catalog enum and in `ErrorCatalogSnapshotTest`'s list; the build tells you when the snapshot needs updating.
- A new endpoint changes `openapi/v1.json`; the build writes the actual document to `target/` and tells you to review and copy it.
- A new ban needs three things: the rule in `BanListArchTest`, a fixture in `starterfixtures`, and a row in `BanCoverageMetaTest`. The build refuses any two without the third.
- The coverage floor and the migration timeouts are this repo's call; change them in one commit that says why.

## Spec-kit

`.specify/memory/constitution.md` is pre-filled. `/speckit.plan` reads it and must not re-plan the stack or
the gates; a plan's "Technical Context" for this repo is the list above plus the feature's own decisions.
