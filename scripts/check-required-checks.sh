#!/usr/bin/env bash
# The forge's required-status-checks list is not a committed file, so assert it against the committed job names
# from the API. A gate that exits non-zero but is not required does not block a merge. Needs GH_TOKEN with read
# access to the repository (the default Actions token suffices on a public repo).
set -euo pipefail
cd "$(dirname "$0")/.."
repo="${GITHUB_REPOSITORY:-$(gh repo view --json nameWithOwner --jq .nameWithOwner)}"
branch="${DEFAULT_BRANCH:-main}"
expected=$(awk '/^jobs:/{injobs=1; next} injobs && /^  [a-zA-Z0-9_-]+:$/{gsub(/[: ]/,""); print}' .github/workflows/ci.yml | sort)
actual=$(gh api "repos/${repo}/rules/branches/${branch}" --jq '.[] | select(.type=="required_status_checks") | .parameters.required_status_checks[].context' | sort)
if [[ -z "$actual" ]]; then
  echo "no required status checks on ${repo}@${branch}; apply .github/rulesets/main.json (scripts/apply-ruleset.sh)" >&2; exit 1
fi
if [[ "$expected" != "$actual" ]]; then
  echo "required checks differ from committed job names" >&2
  echo "expected (ci.yml jobs):"; echo "$expected"; echo "required (forge):"; echo "$actual"; exit 1
fi
echo "required status checks match ci.yml job names: $(tr '\n' ' ' <<<"$expected")"
