#!/usr/bin/env bash
# Static security checks, the same in CI and locally (needs Docker; run from anywhere).
# 1. gitleaks: secrets in the whole git history. 2. Semgrep CE: SAST. 3. Trivy: Dockerfile, fly.toml.
# 4. actionlint (with shellcheck on the run blocks, config in actionlint.yaml) and zizmor (security
#    of the workflows, offline, default persona) on .github/workflows.
set -euo pipefail
cd "$(dirname "$0")/.."

GITLEAKS=zricethezav/gitleaks:v8.28.0
SEMGREP=semgrep/semgrep:1.140.0
TRIVY=aquasec/trivy:0.67.2
ACTIONLINT=rhysd/actionlint:1.7.7
ZIZMOR=ghcr.io/zizmorcore/zizmor:1.16.0

echo "== gitleaks"
docker run --rm -v "$PWD:/repo" "$GITLEAKS" git /repo --redact --no-banner --exit-code 1

# Registry rulesets are fetched at run time (no login), so new rules may appear without a change here.
echo "== semgrep"
docker run --rm -v "$PWD:/src" -w /src "$SEMGREP" semgrep scan --error --metrics=off --quiet \
  --config p/java --config p/typescript --config p/react --config p/dockerfile \
  --config p/github-actions \
  src frontend/src Dockerfile .github

echo "== trivy config"
# Trivy scans one directory: only the deployment files, not the build output and node_modules.
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
cp Dockerfile fly.toml "$tmp"
docker run --rm -v "$tmp:/scan:ro" "$TRIVY" config \
  --exit-code 1 --severity HIGH,CRITICAL /scan

echo "== actionlint"
docker run --rm -v "$PWD:/repo" -w /repo "$ACTIONLINT" -color=false

# Offline: no GitHub token, so no online audits (e.g. known-vulnerable actions). Fails on findings;
# intentional ones carry an inline "zizmor: ignore[rule]" with the reason.
echo "== zizmor"
docker run --rm -v "$PWD:/repo" -w /repo "$ZIZMOR" --offline --no-progress .github/workflows
