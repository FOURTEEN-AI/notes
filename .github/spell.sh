#!/usr/bin/env bash
# Spell check, the same in CI and locally (needs Docker; run from anywhere).
# typos (crate-ci/typos, from its PyPI wheel) over all tracked files: code, docs, workflows. It
# checks identifiers and comments against a list of known misspellings, so German text does not
# produce noise. Exceptions are in _typos.toml.
set -euo pipefail
cd "$(dirname "$0")/.."

PYTHON=python:3.13.9-slim
TYPOS=1.52.2

# Tracked files only (no node_modules, no build output); typos skips binary files itself.
echo "== typos"
git ls-files | docker run --rm -i -v "$PWD:/repo:ro" -w /repo "$PYTHON" sh -c \
  "pip install --quiet --no-cache-dir --root-user-action=ignore --disable-pip-version-check typos==$TYPOS \
   && typos --force-exclude --file-list -"
