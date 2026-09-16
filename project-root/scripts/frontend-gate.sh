#!/usr/bin/env bash
# The frontend job's one step. The micro-frontend is not started yet, and this script says so on every run
# rather than passing silently: a required check that gates nothing is recorded as gating nothing.
# The moment frontend/package.json exists, this script demands a `check` script there and runs it with a
# lockfile-exact install; a frontend with code and no gate fails the build.
set -euo pipefail
cd "$(dirname "$0")/../frontend"
if [[ ! -f package.json ]]; then
  echo "frontend: not started (no frontend/package.json). Nothing gates the frontend yet; frontend/README.md records the decisions taken and the gates to wire."
  exit 0
fi
node -e 'const p=require("./package.json"); if(!p.scripts||!p.scripts.check){console.error("frontend/package.json has no `check` script; a frontend with code and no gate is not allowed"); process.exit(1)}'
[[ -f package-lock.json ]] || { echo "frontend has no package-lock.json; the install must be lockfile-exact" >&2; exit 1; }
npm ci
npm run check
