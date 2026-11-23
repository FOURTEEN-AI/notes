#!/usr/bin/env bash
# Static security checks, the same in CI and locally (needs Docker; run from anywhere).
# 1. gitleaks: secrets in the whole git history. 2. Semgrep CE: SAST. 3. Trivy: Dockerfile, fly.toml.
set -euo pipefail
cd "$(dirname "$0")/.."

GITLEAKS=zricethezav/gitleaks:v8.28.0
SEMGREP=semgrep/semgrep:1.140.0
TRIVY=aquasec/trivy:0.67.2

echo "== gitleaks"
docker run --rm -v "$PWD:/repo" "$GITLEAKS" git /repo --redact --no-banner --exit-code 1

# Registry rulesets are fetched at run time (no login), so new rules may appear without a change here.
# Not checked here: pinning actions to a SHA (mutable-action-tag), left to the workflow hardening.
echo "== semgrep"
docker run --rm -v "$PWD:/src" -w /src "$SEMGREP" semgrep scan --error --metrics=off --quiet \
  --config p/java --config p/typescript --config p/react --config p/dockerfile \
  --config p/github-actions \
  --exclude-rule yaml.github-actions.security.github-actions-mutable-action-tag.github-actions-mutable-action-tag \
  src frontend/src Dockerfile .github

echo "== trivy config"
# Trivy scans one directory: only the deployment files, not the build output and node_modules.
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
cp Dockerfile fly.toml "$tmp"
docker run --rm -v "$tmp:/scan:ro" "$TRIVY" config \
  --exit-code 1 --severity HIGH,CRITICAL /scan
