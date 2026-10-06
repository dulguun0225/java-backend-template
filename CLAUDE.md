# CLAUDE.md

This directory is a Java backend service created from `dulguun0225/java-backend-template`. Read this file,
then `docs/GATES.md`. If this is `backend/` inside a project, the project's own `CLAUDE.md` one level up
covers the repo shape; this one covers the service.

## What is already decided

The stack and every build gate are fixed by the template and by the skills that govern this code. Do not
re-derive or re-design them; do not write scaffolding, lint config, architecture tests or CI from scratch.
They exist, they are green, and `mvn verify` here is the definition of done.

- Java 25, Spring Boot Web MVC, jOOQ against PostgreSQL 18, Flyway, Jackson, Maven. Exact pins, moved by Renovate PRs.
  The tools `mise.toml` pins install at the checksums `mise.lock` records; a tool pin moves in `mise.toml`, then
  `mise lock`, both committed together, and the wall refuses a lock out of date with `mise.toml` or missing a checksum.
- Persistence goes through `Tx` (`tx.read` / `tx.write`) over generated jOOQ. No JPA, no Spring Data, no `JdbcTemplate`, no `@Transactional`.
- Errors are RFC 9457 problems whose `code` comes from a `*ErrorCode` enum; the edge is `ApiExceptionHandler`.
- Every `@RequestBody` binds as `BoundBody<T>`, which only `StrictJsonBodyConverter` reads: an undeclared member (`validation.unknown-field`), a member named after a path variable (`validation.identifier-in-path`) and a wrong JSON type (`validation.wrong-type`) are each an entry of one `validation.failed`, and the service calls `BoundBody.validate` before its transaction. An identifier travels in the path only; each operation binds its own request record, and an update record declares only the fields it writes. "An attempt to change X is refused" is met by X's absence from the update body, never by declaring X and comparing it. Strictness lives in that reader alone: the shared mapper keeps Boot's lenient `spring.jackson` default, because broker messages, once a handoff exists, must tolerate a member a newer producer added.
- Money is the `Money` value object; every rounding names its `RoundingMode` through `RoundingPolicy`.
- Ids are UUIDv7 via `Ids.newId()`; time comes from the injected `Clock`; logging goes through `Log`.
- An `UPDATE` on a table carrying a `version` column goes through `VersionedUpdate`: one statement, the increment and the two-predicate guard, zero affected rows classified as stale or absent. Every other spelling is unwritable.
- One Maven module. The `platform` package is the foundation (no HTTP, depends on no feature); one feature = one package beside it, shaped like `greeting`. Copy its shape, then delete `greeting` and name the new package in this bullet.
- API-only. A frontend, if the project has one, is a separate static deploy and consumes `openapi/v1.json`.

## Skills

Install the rule set these gates implement, once per machine:

```
npx skills add dulguun0225/skills -g -a claude-code -y
```

The skills carry the reasoning and the checks; this directory carries the wired checks. When a skill and a
gate here disagree, the gate is wrong or stale: fix the gate, do not bypass it.

## Working here

Base branch: `main`

The line above names the trunk: work is committed on it directly. Tools read it as written, in that one
form, unindented and once, and only from the `CLAUDE.md` at a repository's root: where this directory
is `backend/` inside a project, the project's own `CLAUDE.md` states the trunk and this line is not read. A repository whose trunk has another name changes the name between the backticks.
This template works on `main`; the services made from it work on `dev`, and `scripts/init.mjs` sets that in
the project `CLAUDE.md` it lifts and, in a standalone service, in this line.

- `node scripts/wall.mjs` is exactly what CI runs: forbidden flags, squawk, the project checks, `mvn verify`, the jOOQ regenerate-twice drift check, the OpenAPI rerun under another timezone, the vacuum ruleset over the committed document, the vulnerability scan. Docker required. Scripts are Node, standard library only; `mise install` gives the pinned Node.
  The project checks are the Node scripts listed one per line, as paths relative to the project root, in `scripts/wall-checks.txt` there (the project root is the directory above this one when it is `backend/` inside a project, otherwise this directory); blank lines and `#` lines are skipped, a listed path that does not exist or a script that exits non-zero fails the wall, and with no such file the step runs nothing. The template ships none.
- `mvn spotless:apply` formats. `mvn -Pcodegen generate-sources` regenerates jOOQ after a migration; it runs before compile, so it works while main code still references a table that does not exist yet.
- A new wire error code goes in a catalog enum and in `ErrorCatalogSnapshotTest`'s list; the build tells you when the snapshot needs updating.
- A feature that calls another feature needs one `caller -> callee` line in `LayeringArchTest.ALLOWED_FEATURE_DEPENDENCIES`, in the same commit as the call, and calls only classes in the callee's `api` package; the build fails until both hold. Add the line; do not edit the layering rules.
- A new table needs an owner row in `TableOwnershipTest.OWNERS`; the build fails until it has one. Any feature may read any table through the generated jOOQ tables; only the owner writes it, and a method that writes may name no other feature's table, so a read of another feature's table goes in a method that starts no write.
- A new endpoint changes `openapi/v1.json`; the build writes the actual document to `target/` and tells you to review and copy it.
- A new ban needs three things: the rule in `BanListArchTest`, a fixture in `starterfixtures`, and a row in `BanCoverageMetaTest`. The build refuses any two without the third.
- The coverage floor and the migration timeouts are this service's call; change them in one commit that says why.
