#!/usr/bin/env bash
# The smell check of docs/plan.md, step 9: habit-hooks over the files that this branch changed
# against `origin/main` (override with SMELLS_BASE), once for the Java code at the repository
# root and once for the frontend as a project of its own. The same script runs in CI (`smells` job
# of review.yml), for the coding agent and in the devcontainer; install-habit-hooks.sh installs the
# tools. The configuration is read from `origin/main`, not from the branch, so a branch cannot
# loosen its own check.
#
# Usage: smells.sh [output-dir]   (default: a new temporary directory)
# Writes <output-dir>/enforced.md (findings in production code that fail the check) and
# <output-dir>/suggested.md (everything else, among it all findings in test files: acceptance
# tests, stages and fakes stay unchanged in phase 2, so they can only be suggestions).
# Exit 0: nothing enforced. 1: enforced findings. 2: a tool failed (counts as red).
set -uo pipefail

base="${SMELLS_BASE:-origin/main}"
root="$(git rev-parse --show-toplevel)"
out="${1:-$(mktemp -d)}"
mkdir -p "$out"
: > "$out/enforced.md"
: > "$out/suggested.md"
tests='(^|/)src/test/|\.(test|spec)\.[jt]sx?$'
status=0

cd "$root"
for project in . frontend; do
  config="$project/.habit-hooks/config.toml"
  git show "$base:$config" > "$out/main-config.toml" 2>/dev/null || continue
  [ -f "$config" ] && cp "$config" "$out/branch-config.toml" || rm -f "$out/branch-config.toml"
  mkdir -p "$project/.habit-hooks"
  cp "$out/main-config.toml" "$config"
  (
    cd "$project"
    if ! habit-sensors --branch "$base" > "$out/findings.json" 2> "$out/sensors.err" \
        && ! jq -e . "$out/findings.json" > /dev/null 2>&1; then
      cat "$out/sensors.err" >&2
      exit 2
    fi
    # A finding of a test file is a suggestion. A run that did not complete stays enforced.
    jq --arg t "$tests" '[.[] | .issues |= map(select(.key | test($t) | not)) | select(.issues | length > 0)]' \
      "$out/findings.json" > "$out/production.json"
    jq --arg t "$tests" '[.[] | select(.smell != "incomplete-run") | .issues |= map(select(.key | test($t))) | select(.issues | length > 0)]' \
      "$out/findings.json" > "$out/test.json"
    mapped=0
    if jq -e 'length > 0' "$out/production.json" > /dev/null; then
      habit-mapper < "$out/production.json" > "$out/mapped.md"
      mapped=$?
      # What the mapper does not enforce (a smell it only suggests) is a suggestion too.
      if [ "$mapped" -eq 0 ]; then target=suggested.md; else target=enforced.md; fi
      { echo "### $project"; cat "$out/mapped.md"; } >> "$out/$target"
    fi
    if jq -e 'length > 0' "$out/test.json" > /dev/null; then
      { echo "### $project (test files)"; habit-mapper < "$out/test.json" || true; } >> "$out/suggested.md"
    fi
    exit "$mapped"
  )
  code=$?
  if [ -f "$out/branch-config.toml" ]; then cp "$out/branch-config.toml" "$config"; else rm -f "$config"; fi
  [ "$code" -gt "$status" ] && status=$code
done
exit "$status"
