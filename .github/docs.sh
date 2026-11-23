#!/usr/bin/env bash
# Documentation checks, the same in CI and locally (needs Docker; run from anywhere).
# 1. markdownlint-cli2: style of all tracked Markdown files (config in .markdownlint-cli2.yaml).
# 2. lychee: relative links and #anchors between the repository files. Offline: external URLs are
#    not fetched, so the result depends only on the repository.
# Not checked: .claude/skills (vendored third-party skills, changed only in their names).
set -euo pipefail
cd "$(dirname "$0")/.."

MARKDOWNLINT=davidanson/markdownlint-cli2:v0.24.2
LYCHEE=lycheeverse/lychee:0.24.2

# Tracked files only (no node_modules, no build output).
mapfile -t files < <(git ls-files -z '*.md' | tr '\0' '\n' | grep -v '^\.claude/skills/')

echo "== markdownlint"
docker run --rm -v "$PWD:/workdir" "$MARKDOWNLINT" "${files[@]}"

echo "== lychee"
docker run --rm -v "$PWD:/repo" -w /repo "$LYCHEE" --offline --include-fragments --no-progress \
  --root-dir /repo "${files[@]}"
