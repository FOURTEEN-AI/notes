#!/usr/bin/env bash
# Visual regression in the pinned Playwright image (same OS and fonts everywhere, amd64 like CI).
# Usage: e2e/visual.sh [--update-snapshots]. Needs Docker and a built frontend (frontend/dist).
set -euo pipefail
cd "$(dirname "$0")/.."
version=$(node -p "require('./package.json').devDependencies['@playwright/test']")
docker run --rm --platform linux/amd64 --ipc=host \
  -e CI=1 -e E2E_VISUAL_IMAGE=1 \
  -v "$PWD:/work" -w /work \
  "mcr.microsoft.com/playwright:v${version}-noble" \
  node node_modules/@playwright/test/cli.js test -c playwright.visual.config.ts "$@"
