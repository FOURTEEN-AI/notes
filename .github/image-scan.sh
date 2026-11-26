#!/usr/bin/env bash
# Builds the image from build/libs/notes.jar and scans it like CI does (docs/plan.md, step 8):
# HIGH and CRITICAL vulnerabilities of OS packages with a fix, and misconfigurations of the
# Dockerfile. Needs Docker and Trivy (.devcontainer/install-trivy.sh).
set -euo pipefail
cd "$(dirname "$0")/.."
docker build -t notes:scan .
trivy image --scanners vuln --pkg-types os --no-progress --severity HIGH,CRITICAL --ignore-unfixed \
  --ignorefile .trivyignore --exit-code 1 notes:scan
trivy config --severity HIGH,CRITICAL --ignorefile .trivyignore --exit-code 1 Dockerfile
