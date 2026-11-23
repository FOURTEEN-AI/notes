#!/usr/bin/env bash
# Supply chain checks, the same in CI and locally (needs Docker, pnpm and the jar from
# `./gradlew build`; run from anywhere).
# 1. SBOMs (CycloneDX) of the runtime dependencies: Java (Gradle plugin) and frontend (pnpm sbom),
#    written to build/sbom. 2. Trivy on the SBOMs: known vulnerabilities and licenses.
#    3. Trivy on the image built from the Dockerfile (local only, never pushed).
set -euo pipefail
cd "$(dirname "$0")/.."

TRIVY=aquasec/trivy:0.67.2 # the same version as in security-static.sh

# Trivy runs in the repo directory, so it reads an optional .trivyignore there (one CVE per line,
# each with a reason and an expiry date). The named volume keeps the vulnerability database
# (about 140 MB): downloaded once, then reused by every call.
trivy() {
  docker run --rm -v trivy-cache:/root/.cache -v "$PWD:/repo:ro" -w /repo \
    -v /var/run/docker.sock:/var/run/docker.sock "$TRIVY" "$@" --quiet
}

echo "== sbom"
./gradlew --quiet cyclonedxDirectBom
# The frontend SBOM reads licenses from node_modules, which `./gradlew build` installs.
# Spec 1.6: Trivy 0.67 does not read the default 1.7.
pnpm --dir frontend --silent sbom --sbom-format cyclonedx --sbom-spec-version 1.6 \
  --sbom-type application --prod --out "$PWD/build/sbom/frontend.cdx.json"

# Vulnerabilities: HIGH and CRITICAL that have a fix. Licenses: Trivy classifies them; HIGH
# (restricted, e.g. GPL, LGPL), CRITICAL (forbidden, e.g. AGPL, SSPL) and UNKNOWN fail, while
# notice (Apache, MIT, BSD) and reciprocal (EPL, MPL, CDDL) pass. Exceptions, both dual licenses
# where we use the other side: logback is EPL-2.0 or LGPL-2.1, jakarta.annotation-api EPL-2.0 or
# GPL-2.0 with Classpath Exception. A new LGPL or GPL library with these exact ids would pass.
for sbom in java frontend; do
  echo "== trivy sbom $sbom"
  trivy sbom --scanners vuln,license --severity UNKNOWN,HIGH,CRITICAL --ignore-unfixed \
    --ignored-licenses LGPL-2.1-only,GPL-2.0-with-classpath-exception \
    --exit-code 1 "build/sbom/$sbom.cdx.json"
done

# The base image (eclipse-temurin:25-jre) is not pinned by digest on purpose: the build pulls the
# current tag, so OS fixes arrive with it and this scan fails only if a fix exists and is not there.
echo "== trivy image"
docker build --quiet --tag notes:scan . > /dev/null
trivy image --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 notes:scan
