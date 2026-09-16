#!/usr/bin/env bash
# Apply the committed main-branch ruleset once, at repo setup. Idempotent: replaces an existing ruleset of the same name.
set -euo pipefail
cd "$(dirname "$0")/.."
repo="${1:-$(gh repo view --json nameWithOwner --jq .nameWithOwner)}"
name=$(python3 -c 'import json;print(json.load(open(".github/rulesets/main.json"))["name"])')
existing=$(gh api "repos/${repo}/rulesets" --jq ".[] | select(.name==\"${name}\") | .id" || true)
if [[ -n "$existing" ]]; then
  gh api --method PUT "repos/${repo}/rulesets/${existing}" --input .github/rulesets/main.json >/dev/null && echo "ruleset '${name}' updated (${existing})"
else
  gh api --method POST "repos/${repo}/rulesets" --input .github/rulesets/main.json >/dev/null && echo "ruleset '${name}' created"
fi
