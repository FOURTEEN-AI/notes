#!/usr/bin/env bash
# Lints the API contract docs/openapi.json with Spectral, the same in CI and locally (needs Docker;
# run from anywhere). The file is generated from the controllers and compared with them by
# OpenApiContractTest (`./gradlew test`); this script only judges the file as it is checked in:
# rules in config/spectral/ruleset.yaml (spectral:oas, every warning fails).
set -euo pipefail
cd "$(dirname "$0")/.."

SPECTRAL=stoplight/spectral:6.15.0

echo "== spectral"
docker run --rm -v "$PWD:/repo:ro" -w /repo "$SPECTRAL" lint docs/openapi.json \
  --ruleset config/spectral/ruleset.yaml --fail-severity=warn
