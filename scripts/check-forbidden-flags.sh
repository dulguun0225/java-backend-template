#!/usr/bin/env bash
# No preview-feature compiler or launcher flag, and no Java agent, in any build, container, compose, CI or
# script file. ArchUnit reads bytecode and cannot see either; this grep is the gate. ForbiddenFlagsTest runs the
# same check over the build files from inside the build.
set -euo pipefail
cd "$(dirname "$0")/.."
tokens='(--enable-preview|-javaagent|opentelemetry-javaagent|otel-javaagent|aws-opentelemetry-agent)'
# Relative to this directory, whether it is the template root or a project's backend/ (git ls-files is cwd-relative).
files=$(git ls-files -- 'pom.xml' '.mvn/*' 'Dockerfile' 'scripts/*' '.github/workflows/*' 'project-root/*' | grep -v 'check-forbidden-flags.sh' || true)
# Vendored into a project: the project's compose and workflows one level up are deploy files too.
if [[ "$(git rev-parse --show-toplevel)" != "$(pwd -P)" ]]; then
  files="$files $(git ls-files -- '../compose*.yaml' '../.github/workflows/*' || true)"
fi
[[ -n "${files// }" ]] || { echo 'no build or deploy files found to scan' >&2; exit 1; }
if grep -nE "$tokens" $files; then
  echo "forbidden flag found (see lines above)" >&2; exit 1
fi
echo "no preview or agent flags in build and deploy files"
