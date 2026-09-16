# java-backend-template

The `backend/` of a repo whose code is written by LLM agents and read line by line by nobody: a Java
service on Spring Boot Web MVC, jOOQ over PostgreSQL 18, Flyway, Maven, Java 25, with every build gate the
[`dulguun0225/skills`](https://github.com/dulguun0225/skills) rule set names as enforceable already wired and
green. A new service spends its first tokens on domain code, not on scaffolding.

The skills carry the decisions and the reasoning. This repo carries the consequences: the pom, the
executable ban list, the migration lint, the contract snapshots, the wall script. The scripts are Node,
not bash, so the wall and the scaffold run the same on Linux, macOS and Windows; Node is pinned in `mise.toml`
beside Java and Maven, and no script here has a dependency to install. `docs/GATES.md` maps
each gate to the directive it implements and lists, by name, what no gate here reaches.

## Use it as a project's backend

The intended shape is one project repo holding one service and one micro-frontend. This template is the
service; it is vendored into `backend/` and lifts the project-level files (root CI with `backend` and
`frontend` jobs, branch ruleset, compose, `frontend/` stub, spec-kit constitution, project `CLAUDE.md`)
one level up:

```bash
mkdir some_service_1 && cd some_service_1 && git init -b main && git commit --allow-empty -m "init: empty root"   # subtree add needs a HEAD
git subtree add --prefix backend https://github.com/dulguun0225/java-backend-template.git main --squash
cd backend
node scripts/init.mjs --package com.acme.someservice1 --name some_service_1   # rename + lift project-root/ to ..
mvn -Pcodegen generate-sources && mvn verify                           # regenerate jOOQ under the new package; the wall
cd .. && git add -A && git commit -m "init: some_service_1 from java-backend-template"
gh repo create acme/some_service_1 --private --source=. --push
node scripts/apply-ruleset.mjs                                              # PR + backend + frontend checks required on main
```

The `java-backend-rules` skill ships this sequence as one command, `scripts/new-backend.mjs`, pinned to a
recorded commit of this template; prefer it over retyping the lines above.

`git subtree` keeps the template's history, so `git subtree pull --prefix backend … main --squash` can bring
later gate changes in; expect to resolve the package rename when it does. Then install the skills for the
agent (`npx skills add dulguun0225/skills -a claude-code -y`) and, for spec-kit, `specify init --here`; the
pre-filled `.specify/memory/constitution.md` survives it.

**Scaffold before spec-kit's first command.** The order is: this scaffold, `specify init --here`,
`/speckit.constitution`, `/speckit.specify`, `/speckit.plan`, `/speckit.tasks`, `/speckit.implement`.
`/speckit.constitution` then amends Article VII, the project's own decisions, and leaves Articles I–VI
alone: they restate what `mvn verify` already enforces, and `/speckit.plan` inherits them instead of
re-planning the stack. Run spec-kit first and the constitution it writes is the one that stays: `init.mjs`
never overwrites, so the platform articles are dropped and the plan will re-decide what the template has
already decided.

## Use it standalone

`gh repo create acme/some_service_1 --template dulguun0225/java-backend-template --clone`, then
`scripts/init.mjs` as above; in a repository root it renames and does nothing else, and the template's own
`.github/workflows/ci.yml` is the service's CI.

Toolchain: `mise install` reads `mise.toml` (Java 25, Maven 3.9, Node 24, osv-scanner). Docker is needed for the wall.

## What is in the box

| Path | What |
|---|---|
| `pom.xml` | One Maven module; every gate wired here |
| `src/main/java/.../platform/` | The platform tier: `Money`, `RoundingPolicy`, `Ids` (UUIDv7), `Tx` (the one transaction seam), the RFC 9457 error contract, the typed logging facade, `KeysetPager`. Depends on no feature; `LayeringArchTest` pins it |
| `src/main/java/.../db/` | The generated jOOQ tree, committed, regenerated from `src/main/resources/db/migration/` by `mvn -Pcodegen generate-sources` |
| `src/main/java/.../` | `Application`, the correlation filter, the exception handler and its error catalog, and one package per feature, `greeting` being the worked example |
| `src/test/java/` | Every architecture, contract, convention and integration test; `...fixtures/` holds one violating fixture per ban rule |
| `openapi/v1.json` | The committed, normalized contract the build diffs; a frontend's generated client types come from it |
| `codegen/` | The jOOQ codegen runner, launched as a Java source-file program so it needs nothing compiled first |
| `scripts/wall.mjs` | The whole wall as one command; the template's CI and a project's `backend` job both run it |
| `scripts/` | The wall's parts: forbidden flags, squawk, codegen drift, osv-scanner; `init.mjs`; and `_lib.mjs`, the helpers they share. Node, standard library only |
| `project-root/` | What a project needs at its root: `.github/workflows/ci.yml` (backend + frontend jobs), `.github/rulesets/main.json`, `compose.yaml`, `frontend/README.md`, `.specify/memory/constitution.md`, `CLAUDE.md`, `scripts/` (ruleset, pins, required-checks, frontend gate). `init.mjs` lifts it when vendored |
| `docs/GATES.md` | Gate-to-directive map and the named gaps |
| `CLAUDE.md` | What the agent reads first in this directory |

## The gates, in one paragraph

Compile wall (Error Prone, NullAway, JSpecify; empty catch and dropped future are errors). Enforcer (Java
pin, no dynamic versions, jqwik ceiling, banned dependencies). Spotless. An executable ban list with a
coverage meta-test and a negative-control fixture per rule. Layering. Migration conventions with negative
fixtures, plus squawk. jOOQ regenerated twice from the migrations and diffed. Error catalog snapshot.
OpenAPI document normalized and snapshotted, rerun under another timezone. Error edge tests: every error
coded, the 500 leaks nothing and its incident id resolves to one log event. Property tests on `Money`.
Integration tests on real PostgreSQL. JaCoCo floor. Licence allowlist. SBOM plus osv-scanner with a
committed suppression inventory. At the project root: SHA-pinned actions, a required-checks assertion
against the forge, a frontend job that refuses ungated frontend code, Renovate as the named path for
moving a pin.

## Adding a feature

Copy the `greeting` package's shape: a migration under `src/main/resources/db/migration`, `mvn -Pcodegen
generate-sources`, a `*ErrorCode` enum (add it to `ErrorCatalogSnapshotTest`), a service that goes through
`Tx`, a controller, an `*IT` against Testcontainers. The build tells you when the error-catalog or OpenAPI
snapshot needs a deliberate update and writes the new copy under `target/`. Then delete `greeting`.

## Provenance

Pins recorded 2026-09-16: Java 25 LTS on BellSoft Liberica (the JDK Spring recommends; `mise.toml`, `ci.yml` and the
Dockerfile name the same vendor), Spring Boot 4.1.1 (newest GA line), Tomcat overridden to 11.0.26 for
three advisories the Boot BOM had not caught up with. The wiring was lifted from a production repo on the
same stack and reduced to what the skills require; every gate was run green here before the first commit.
