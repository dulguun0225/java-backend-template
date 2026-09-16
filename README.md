# java-backend-template

A GitHub template for a Java backend whose code is written by LLM agents and read line by line by nobody.
Spring Boot Web MVC, jOOQ over PostgreSQL 18, Flyway, Maven, Java 25. Every build gate the
[`dulguun0225/skills`](https://github.com/dulguun0225/skills) rule set names as enforceable is already
wired and green, so a new project spends its first tokens on domain code, not on scaffolding.

The skills carry the decisions and the reasoning. This repo carries the consequences: the poms, the
executable ban list, the migration lint, the contract snapshots, the CI. `docs/GATES.md` maps each gate to
the directive it implements and lists, by name, what no gate here reaches.

## Create a project

```bash
gh repo create my-org/billing --template dulguun0225/java-backend-template --private --clone
cd billing
scripts/init.sh --package com.acme.billing --name billing     # renames package, group, modules
mvn -Pcodegen -pl billing-platform process-test-classes        # regenerate jOOQ under the new package
mvn verify                                                     # the whole wall; Docker required
git add -A && git commit -m "init: billing from java-backend-template"
scripts/apply-ruleset.sh                                        # main-branch protection: PR + the `wall` check
```

Then install the skills for the agent (`npx skills add dulguun0225/skills -a claude-code -y`) and, for
spec-kit, run `specify init --here`; the pre-filled `.specify/memory/constitution.md` survives it.

Toolchain: `mise install` reads `mise.toml` (Java 25, Maven 3.9). Docker is needed for `mvn verify`,
codegen and the drift check.

## What is in the box

| Path | What |
|---|---|
| `starter-platform/` | The platform tier: `Money`, `RoundingPolicy`, `Ids` (UUIDv7), `Tx` (the one transaction seam), the RFC 9457 error contract, the typed logging facade, `KeysetPager`, the Flyway migrations and the generated jOOQ tree |
| `starter-app/` | The deployable: `Application`, the correlation filter, the exception handler and its error catalog, the `greeting` worked-example feature, `openapi/v1.json`, and every architecture and contract test |
| `scripts/` | The CI steps as shell: action pins, forbidden flags, squawk, codegen drift, osv-scanner, required-checks assertion, ruleset apply, `init.sh` |
| `.github/workflows/ci.yml` | One job, `wall`, everything on exit codes, every action SHA-pinned |
| `docs/GATES.md` | Gate-to-directive map and the named gaps |
| `CLAUDE.md`, `.specify/memory/constitution.md` | What the agent and spec-kit read first |

## The gates, in one paragraph

Compile wall (Error Prone, NullAway, JSpecify; empty catch and dropped future are errors). Enforcer (Java
pin, no dynamic versions, jqwik ceiling, banned dependencies). Spotless. An executable ban list over both
modules with a coverage meta-test and a negative-control fixture per rule. Layering. Migration conventions
with negative fixtures, plus squawk. jOOQ regenerated twice from the migrations and diffed. Error catalog
snapshot. OpenAPI document normalized and snapshotted, rerun under another timezone. Error edge tests: every
error coded, the 500 leaks nothing and its incident id resolves to one log event. Property tests on `Money`.
Integration tests on real PostgreSQL. JaCoCo floor. Licence allowlist. SBOM plus osv-scanner with a
committed suppression inventory. Required-checks assertion against the forge. Renovate as the named path
for moving a pin.

## Adding a feature

Copy the `greeting` package's shape: a migration under `db/migration`, regenerate jOOQ, a `*ErrorCode`
enum (add it to `ErrorCatalogSnapshotTest`), a service that goes through `Tx`, a controller, an `*IT`
against Testcontainers. The build tells you when the error-catalog or OpenAPI snapshot needs a deliberate
update and writes the new copy under `target/`. Then delete `greeting`.

## Provenance

Pins recorded 2026-09-16: Java 25 LTS, Spring Boot 4.1.1 (newest GA line), Tomcat overridden to 11.0.26 for
three advisories the Boot BOM had not caught up with. The wiring was lifted from a production repo on the
same stack and reduced to what the skills require; every gate was run green here before the first commit.
