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
- Every `@RequestBody` binds as `BoundBody<T>`, which only `StrictJsonBodyConverter` reads: an undeclared member (`validation.unknown-field`), a member named after a path variable (`validation.identifier-in-path`) and a wrong JSON type (`validation.wrong-type`) are each an entry of one `validation.failed`, and the service calls `BoundBody.validate` before its transaction. An identifier travels in the path only; each operation binds its own request record, and an update record declares only the fields it writes. "An attempt to change X is refused" is met by X's absence from the update body, never by declaring X and comparing it. Strictness lives in that reader alone: the shared mapper keeps Boot's lenient `spring.jackson` default, because broker messages, once a handoff exists, must tolerate a member a newer producer added.
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

Base branch: `main`

The line above names the trunk: feature branches are cut from it and merged back into it. `build-feature`
reads it as written, in that one form, unindented and once, and only from the `CLAUDE.md` at a repository's
root: where this directory is `backend/` inside a project, the project's own `CLAUDE.md` states the trunk
and this line is not read. A repository whose trunk has another name changes the name between the backticks.

- `node scripts/wall.mjs` is exactly what CI runs: forbidden flags, squawk, the traceability gate's canary and then the traceability gate itself (that order: a gate that cannot fail proves nothing), `mvn verify`, the jOOQ regenerate-twice drift check, the OpenAPI rerun under another timezone, the vacuum ruleset over the committed document, the vulnerability scan. Docker required. Scripts are Node, standard library only; `mise install` gives the pinned Node.
- `mvn spotless:apply` formats. `mvn -Pcodegen generate-sources` regenerates jOOQ after a migration; it runs before compile, so it works while main code still references a table that does not exist yet.
- A feature's `spec.md` is written by a domain expert with stock spec-kit (`/speckit-specify`, `/speckit-clarify`);
  build work picks up at `/speckit-plan`, and no later stage edits `spec.md`.
- A requirement citation written anywhere outside its own `specs/<NNN>-<name>/` directory — code, test, migration
  comment, `docs/GATES.md` — is qualified: `NNN/FR-nnn` / `NNN/SC-nnn`, where `NNN` is the feature directory's
  numeric prefix. Ids collide across features, so a bare `FR-nnn` names one requirement per feature and none of
  them. Inside a feature's own `specs/<NNN>-<name>/` a bare id is legal and means that feature's own, so it has
  to be one that feature's `spec.md` defines; a reference to another feature's id is qualified there too, and
  the spaced form (`NNN` and a space where the slash belongs) is not a citation anywhere. A token qualified by
  something that is **not** a feature prefix is a third thing again — `CAP-NC02-04/FR-034a`, where the qualifier
  is an uppercase letter followed by uppercase letters, digits and hyphens, so it can never be read as a
  feature's three digits. It names a requirement of another document, and nothing here declares, pins or
  resolves any such document, so the token is **prose**: not a citation, not a bare id, resolving nothing,
  covering nothing, planning nothing, needing no declaration. The gate recognises the shape only so that it can
  drop it — unrecognised it would read as the bare id it wraps, and inside a feature directory a bare id means
  that feature's own, which is how a token pointing outside would quietly pass for a local citation, a coverage
  and a task. `node scripts/check-traceability.mjs`
  reads that rule and the coverage behind it; `--report` prints the matrix and the
  spec→tasks gap. Every requirement of a feature
  whose `tasks.md` has no open box is cited from a file under `src/test/` or carries a row in
  `specs/trace-waivers.tsv`, which is three columns — `NNN/ID<TAB>kind<TAB>reason` — and the kind is exactly
  one of two. `external`: the criterion cannot be witnessed from inside this repository at all (a consumer
  service's behaviour, caller topology, a production baseline, an organisational outcome). `deferred`: the
  requirement is specified and deliberately not built yet, and the reason names where that deferral is
  recorded — a named-gap row in `docs/GATES.md`, a scope boundary in the feature's `plan.md`, the owning
  capability — so a reader can go and check it. There is no third kind, and a requirement that is merely
  untested is neither of them: it gets a test. Separately, every requirement of **every** feature that has a
  `tasks.md` is named by some task in it — bare, since a task file sits inside its own feature directory, or
  qualified — or carries a waiver row; `plan.md` and commit messages stay ungated. `--report` ends with the
  `deferred` ids as a list of their own, which is this repo's list of specified-but-unbuilt requirements. The
  template ships neither list `specs/` holds — `trace-waivers.tsv`, `trace-legacy-files.tsv` — and no `specs/`
  tree either: each list is optional to the gate, and the first feature that needs a row creates it. The gate
  runs regardless, with zero defined ids, and a bare id still fails. It is a step in `scripts/wall.mjs`;
  `docs/GATES.md` carries the caveats it cannot reach.
- A new wire error code goes in a catalog enum and in `ErrorCatalogSnapshotTest`'s list; the build tells you when the snapshot needs updating.
- A new table needs an owner row in `TableOwnershipTest.OWNERS`; the build fails until it has one. Any feature may read any table through the generated jOOQ tables; only the owner writes it, and a method that writes may name no other feature's table, so a read of another feature's table goes in a method that starts no write.
- A new endpoint changes `openapi/v1.json`; the build writes the actual document to `target/` and tells you to review and copy it.
- A new ban needs three things: the rule in `BanListArchTest`, a fixture in `starterfixtures`, and a row in `BanCoverageMetaTest`. The build refuses any two without the third.
- The coverage floor and the migration timeouts are this service's call; change them in one commit that says why.
