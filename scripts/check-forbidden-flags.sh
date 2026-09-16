#!/usr/bin/env bash
# No preview-feature compiler or launcher flag, and no Java agent, in any build, container, compose, CI or
# script file. ArchUnit reads bytecode and cannot see either; this grep is the gate. ForbiddenFlagsTest runs the
# same check over the build files from inside the build.
set -euo pipefail
cd "$(dirname "$0")/.."
tokens='(--enable-preview|-javaagent|opentelemetry-javaagent|otel-javaagent|aws-opentelemetry-agent)'
files=$(git ls-files -- 'backend/pom.xml' 'backend/.mvn/*' 'backend/Dockerfile' 'compose*.yaml' '.github/workflows/*' 'scripts/*' 'frontend/*' | grep -v 'scripts/check-forbidden-flags.sh')
if grep -nE "$tokens" $files; then
  echo "forbidden flag found (see lines above)" >&2; exit 1
fi
echo "no preview or agent flags in build and deploy files"
