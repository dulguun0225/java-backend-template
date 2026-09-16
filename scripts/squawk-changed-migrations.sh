#!/usr/bin/env bash
# squawk over the Flyway migrations a change adds or edits, gated on exit code. Usage: squawk-changed-migrations.sh <base-sha>
# With no base (or an unknown one) every committed migration is linted, which is the right answer on a fresh repo.
set -euo pipefail
cd "$(dirname "$0")/.."
SQUAWK_VERSION=2.65.0
base="${1:-}"
if [[ -n "$base" && "$base" != 0000000000000000000000000000000000000000 ]] && git cat-file -e "$base" 2>/dev/null; then
  mapfile -t files < <(git diff --name-only --diff-filter=AM "$base"...HEAD -- 'backend/src/main/resources/db/migration/*.sql')
else
  mapfile -t files < <(git ls-files -- 'backend/src/main/resources/db/migration/*.sql')
fi
if [[ ${#files[@]} -eq 0 ]]; then echo "no migrations changed"; exit 0; fi
printf 'linting %s migration(s)\n' "${#files[@]}"
npx --yes "squawk-cli@${SQUAWK_VERSION}" -c .squawk.toml "${files[@]}"
