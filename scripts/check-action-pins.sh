#!/usr/bin/env bash
# Every `uses:` in every workflow references a 40-hex commit SHA, never a tag. A tag moves; a SHA does not.
# Pinning the caller does not pin a reusable workflow's own callees: review those by hand when adding one.
set -euo pipefail
cd "$(dirname "$0")/.."
status=0
while IFS= read -r line; do
  ref=$(sed -E 's/.*uses:[[:space:]]*([^[:space:]#]+).*/\1/' <<<"$line")
  if [[ "$ref" == ./* ]]; then continue; fi                # local composite action
  if [[ ! "$ref" =~ @[0-9a-f]{40}$ ]]; then
    echo "not SHA-pinned: $line" >&2; status=1
  fi
done < <(grep -rhE '^\s*-?\s*uses:' .github/workflows/)
[[ $status -eq 0 ]] && echo "all actions SHA-pinned"
exit $status
