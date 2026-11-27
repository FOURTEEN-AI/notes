#!/usr/bin/env bash
# Prints the name N of a preview: the issue of an agent branch (agent/issue-<n>), else the pull
# request. The app is notes-fourteen-it-N, the database N with underscores.
# Usage: preview-name.sh <head-ref> <pull-request-number>
set -euo pipefail
if [[ "$1" =~ ^agent/issue-([0-9]+)$ ]]; then echo "issue-${BASH_REMATCH[1]}"; else echo "pr-$2"; fi
