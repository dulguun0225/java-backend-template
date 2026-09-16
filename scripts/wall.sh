#!/usr/bin/env bash
# The backend wall, as one command. Both the template's own CI and a project's `backend` job run exactly this,
# so the two workflows cannot drift on what "green" means. Needs Docker (Testcontainers) and network.
# Usage: scripts/wall.sh [base-sha]   (the base sha scopes the migration lint to the change; omit for all)
set -euo pipefail
cd "$(dirname "$0")/.."
base="${1:-}"
step() { printf '\n==> %s\n' "$*"; }

step "No preview features, no Java agents, in any build or deploy file"
scripts/check-forbidden-flags.sh

step "Migration lint (squawk)"
scripts/squawk-changed-migrations.sh "$base"

step "Build wall: compile wall, formatter, architecture tests, unit and integration tests, coverage, licence gate"
mvn -B -ntp verify

step "jOOQ classes match the committed migrations (regenerate twice, byte-identical)"
scripts/check-codegen-drift.sh

step "OpenAPI document is byte-reproducible under a different timezone and locale"
TZ=Pacific/Kiritimati LANG=tr_TR.UTF-8 LC_ALL=tr_TR.UTF-8 mvn -B -ntp verify -Dit.test=OpenApiSnapshotIT -Dtest=NoSuchTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true -Dspotless.check.skip=true -Dlicense.skip=true \
  -Dcyclonedx.skip=true -Denforcer.skip=true

step "Dependency vulnerabilities (osv-scanner over the SBOM)"
scripts/osv-scan.sh

step "backend wall green"
