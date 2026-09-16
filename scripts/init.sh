#!/usr/bin/env bash
# Rename the template into a project: base package, Maven groupId and artifact prefix. Run once, right after
# creating the repo from the template, then commit. Usage:
#   scripts/init.sh --package com.acme.billing --name billing [--group com.acme]
# Everything else (the gates, the CI, the scripts) is deliberately identical across projects.
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
[[ "$name" =~ ^[a-z][a-z0-9-]*$ ]] || { echo "--name must be lowercase, digits and hyphens" >&2; exit 2; }
[[ -n "$group" ]] || group="${pkg%.*}"

old_pkg=com.example.starter; old_path=com/example/starter; old_group=com.example; old_name=starter
new_path=${pkg//./\/}

files=$(git ls-files | grep -v '^scripts/init.sh$')
# 1. package and group strings (generated jOOQ included: it carries the package in every file)
sed -i "s/${old_pkg//./\\.}/${pkg}/g" $files
sed -i "s#${old_path}#${new_path}#g" $files
sed -i "s#<groupId>${old_group//./\\.}</groupId>#<groupId>${group}</groupId>#g; s#<exclude>${old_group//./\\.}:\*</exclude>#<exclude>${group}:*</exclude>#g; s#<ignore>${old_group//./\\.}:\*</ignore>#<ignore>${group}:*</ignore>#g" $files
# 2. artifact and image names; the spring-boot-starter-* artifacts share the word and are excluded by anchoring
sed -i -E "s#<artifactId>${old_name}</artifactId>#<artifactId>${name}</artifactId>#; s#<name>${old_name}</name>#<name>${name}</name>#; s/^(\s*name): ${old_name}$/\1: ${name}/; s/\"title\" : \"${old_name}\"/\"title\" : \"${name}\"/; s/POSTGRES_(DB|USER|PASSWORD): ${old_name}$/POSTGRES_\1: ${name}/; s#-U ${old_name} -d ${old_name}#-U ${name} -d ${name}#; s#postgresql://postgres:5432/${old_name}#postgresql://postgres:5432/${name}#; s/(USERNAME|PASSWORD): ${old_name}$/\1: ${name}/; s/image: ${old_name}:dev/image: ${name}:dev/; s#target/${old_name}-\*\.jar#target/${name}-*.jar#; s/title\(\"${old_name}\"\)/title(\"${name}\")/" $files
# 3. directories: the base package tree and its test-only siblings (starterfixtures, startertest), which share the
#    leaf name so a rename of the package prefix renames them too
old_parent=$(dirname "$old_path"); old_leaf=$(basename "$old_path")
new_parent=$(dirname "$new_path"); new_leaf=$(basename "$new_path")
# prose that names the test-only sibling packages by their leaf (docs, CLAUDE.md)
sed -i -E "s/\b${old_leaf}(fixtures|test)\b/${new_leaf}\1/g" $files
for src in main test; do
  for d in "src/${src}/java/${old_parent}/${old_leaf}"*; do
    [[ -d "$d" ]] || continue
    suffix="${d##*/${old_leaf}}"
    t="src/${src}/java/${new_parent}/${new_leaf}${suffix}"
    mkdir -p "$(dirname "$t")"; git mv "$d" "$t"
  done
done
find . -type d -empty -not -path './.git/*' -delete
echo "renamed: package ${pkg}, group ${group}, artifact ${name}"
echo "next: mvn -Pcodegen generate-sources && mvn verify, then commit"
