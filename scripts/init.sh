#!/usr/bin/env bash
# Turn the template into a service: base package, Maven groupId and artifact name. Run once, then commit.
#
#   scripts/init.sh --package com.acme.someservice1 --name some_service_1 [--group com.acme]
#
# Two modes, detected from git:
#   standalone  this directory is the repository root: rename only.
#   vendored    this directory is <project>/backend (added with `git subtree add --prefix backend ...`): rename,
#               then lift project-root/ one level up — root CI with backend+frontend jobs, ruleset, compose,
#               frontend/ stub, spec-kit constitution, project CLAUDE.md — never overwriting a file that exists,
#               and remove the template's own .github/ and renovate.json, which only mean something at a root.
# Everything else (the gates, the scripts) is deliberately identical across services.
set -euo pipefail
cd "$(dirname "$0")/.."
pkg=""; name=""; group=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --package) pkg="$2"; shift 2;;
    --name) name="$2"; shift 2;;
    --group) group="$2"; shift 2;;
    *) echo "unknown argument $1" >&2; exit 2;;
  esac
done
[[ "$pkg" =~ ^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$ ]] || { echo "--package must be a lowercase dotted java package" >&2; exit 2; }
[[ "$name" =~ ^[a-z][a-z0-9_-]*$ ]] || { echo "--name must be lowercase letters, digits, hyphens or underscores" >&2; exit 2; }
[[ -n "$group" ]] || group="${pkg%.*}"

top=$(git rev-parse --show-toplevel)
here=$(pwd -P)
mode=standalone
if [[ "$here" != "$top" ]]; then
  [[ "$(dirname "$here")" == "$top" ]] || { echo "vendored mode expects this directory to sit directly under the project root ($top)" >&2; exit 2; }
  mode=vendored
fi

old_pkg=com.example.starter; old_path=com/example/starter; old_group=com.example; old_name=starter
new_path=${pkg//./\/}
old_leaf=$(basename "$old_path"); new_leaf=$(basename "$new_path")

# every file here, tracked or not, except this script
files=$(git ls-files -co --exclude-standard . | grep -v '^scripts/init.sh$')

# 1. package and group strings (generated jOOQ included: it carries the package in every file)
sed -i "s/${old_pkg//./\\.}/${pkg}/g" $files
sed -i "s#${old_path}#${new_path}#g" $files
sed -i "s#<groupId>${old_group//./\\.}</groupId>#<groupId>${group}</groupId>#g; s#<exclude>${old_group//./\\.}:\*</exclude>#<exclude>${group}:*</exclude>#g; s#<ignore>${old_group//./\\.}:\*</ignore>#<ignore>${group}:*</ignore>#g" $files
# 2. the service's name wherever it is a name (artifact, jar, image, compose, application, OpenAPI title); the
#    spring-boot-starter-* artifacts share the word and are excluded by anchoring
sed -i -E "s#<artifactId>${old_name}</artifactId>#<artifactId>${name}</artifactId>#; s#<name>${old_name}</name>#<name>${name}</name>#; s/^(\s*name): ${old_name}$/\1: ${name}/; s/\"title\" : \"${old_name}\"/\"title\" : \"${name}\"/; s/POSTGRES_(DB|USER|PASSWORD): ${old_name}$/POSTGRES_\1: ${name}/; s#-U ${old_name} -d ${old_name}#-U ${name} -d ${name}#; s#postgresql://postgres:5432/${old_name}#postgresql://postgres:5432/${name}#; s/(USERNAME|PASSWORD): ${old_name}$/\1: ${name}/; s/image: ${old_name}:dev/image: ${name}:dev/; s#target/${old_name}-\*\.jar#target/${name}-*.jar#; s/title\(\"${old_name}\"\)/title(\"${name}\")/" $files
# 3. prose that names the test-only sibling packages by their leaf
sed -i -E "s/\b${old_leaf}(fixtures|test)\b/${new_leaf}\1/g" $files
# 4. directories: the base package tree and its test-only siblings, which share the leaf name
old_parent=$(dirname "$old_path"); new_parent=$(dirname "$new_path")
for src in main test; do
  for d in "src/${src}/java/${old_parent}/${old_leaf}"*; do
    [[ -d "$d" ]] || continue
    suffix="${d##*/${old_leaf}}"
    t="src/${src}/java/${new_parent}/${new_leaf}${suffix}"
    mkdir -p "$(dirname "$t")"; mv "$d" "$t"
  done
done
find src -type d -empty -delete

echo "renamed: package ${pkg}, group ${group}, artifact ${name} (${mode})"

if [[ "$mode" == vendored ]]; then
  # 5. lift the project-level files to the project root, never overwriting; report what was left alone
  while IFS= read -r -d '' f; do
    rel="${f#project-root/}"
    if [[ -e "../$rel" ]]; then
      echo "kept existing ../$rel (template copy not applied)"
    else
      mkdir -p "$(dirname "../$rel")"; cp -p "$f" "../$rel"
    fi
  done < <(find project-root -type f -print0)
  rm -rf project-root .github renovate.json
  echo "lifted project-root/ to $(dirname "$here"); removed the template's own .github/ and renovate.json from $(basename "$here")/"
  echo "next: mvn -Pcodegen generate-sources && mvn verify here; then at the project root: git add -A, commit, scripts/apply-ruleset.sh"
else
  echo "next: mvn -Pcodegen generate-sources && mvn verify, then commit"
fi
