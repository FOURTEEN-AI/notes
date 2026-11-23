#!/usr/bin/env bash
# DAST: OWASP ZAP baseline scan (passive, no attacks) against the built jar, the same in CI and
# locally (needs Docker, Java and build/libs/notes.jar: run `./gradlew bootJar` first). Starts
# postgres:17-alpine, the GitHub stub of the e2e tests (the real API is never called) and the jar
# like frontend/e2e/stack.mjs does, then runs ZAP twice, both with the login dev/dev sent as an
# Authorization header:
# 1. zap-baseline.py spiders the pages from / and judges the responses;
# 2. zap-api-scan.py -S (safe mode, passive only) calls every operation of docs/openapi.json
#    with generated values, so also POST and DELETE on the scratch database.
# Levels per rule are in config/zap/rules.tsv (IGNORE, INFO, WARN, FAIL); a WARN or a FAIL fails
# the script. The HTML reports are written to build/dast/. Needs node for the stub.
set -euo pipefail
cd "$(dirname "$0")/.."

ZAP=ghcr.io/zaproxy/zaproxy:2.17.0
POSTGRES=postgres:17-alpine
JAR=build/libs/notes.jar
DB=notes-dast-postgres
PORT=18082
STUB_PORT=18092
APP="http://127.0.0.1:$PORT"
AUTH="Basic $(printf 'dev:dev' | base64)"

mkdir -p build
[ -f "$JAR" ] || { echo "$JAR is missing: run ./gradlew bootJar" >&2; exit 1; }

app_pid=
stub_pid=
cleanup() {
  [ -n "$app_pid" ] && kill "$app_pid" 2> /dev/null || true
  [ -n "$stub_pid" ] && kill "$stub_pid" 2> /dev/null || true
  docker rm -f "$DB" > /dev/null 2>&1 || true
}
trap cleanup EXIT

docker rm -f "$DB" > /dev/null 2>&1 || true
docker run -d --rm --name "$DB" -p 127.0.0.1::5432 -e POSTGRES_USER=notes \
  -e POSTGRES_PASSWORD=notes -e POSTGRES_DB=notes "$POSTGRES" > /dev/null
db_port=$(docker port "$DB" 5432/tcp | head -n 1 | sed 's/.*://')
for i in $(seq 1 60); do
  docker exec "$DB" pg_isready -U notes -h 127.0.0.1 > /dev/null 2>&1 && break
  [ "$i" = 60 ] && { echo "PostgreSQL does not start" >&2; exit 1; }
  sleep 1
done

node --input-type=module -e \
  "import { startGitHubStub } from './frontend/e2e/github-stub.mjs'; await startGitHubStub($STUB_PORT)" &
stub_pid=$!

java -jar "$JAR" --server.port="$PORT" \
  --spring.datasource.url="jdbc:postgresql://127.0.0.1:$db_port/notes" \
  --spring.datasource.username=notes --spring.datasource.password=notes \
  --spring.security.user.name=dev --spring.security.user.password=dev \
  --github.api-url="http://127.0.0.1:$STUB_PORT" --github.token=stub > build/dast-app.log 2>&1 &
app_pid=$!
for i in $(seq 1 120); do
  curl -sf "$APP/actuator/health" > /dev/null && break
  kill -0 "$app_pid" 2> /dev/null || { cat build/dast-app.log >&2; echo "the app exited" >&2; exit 1; }
  [ "$i" = 120 ] && { echo "the app does not start" >&2; exit 1; }
  sleep 1
done

# ZAP runs as a user of its own and writes into /zap/wrk.
out=build/dast
rm -rf "$out"
mkdir -p "$out"
chmod 777 "$out"
cp config/zap/rules.tsv docs/openapi.json "$out"
# Sends the login with every request, also the ones of the spider and the passive scan. A file,
# because the value has a space, which the -z option cannot carry.
cat > "$out/zap.properties" <<PROPERTIES
replacer.full_list(0).description=login
replacer.full_list(0).enabled=true
replacer.full_list(0).matchtype=REQ_HEADER
replacer.full_list(0).matchstr=Authorization
replacer.full_list(0).regex=false
replacer.full_list(0).replacement=$AUTH
PROPERTIES
zap_options="-configfile /zap/wrk/zap.properties"
zap() {
  docker run --rm --network host -v "$PWD/$out:/zap/wrk:rw" "$ZAP" "$@"
}

status=0
echo "== zap baseline"
zap zap-baseline.py -t "$APP" -c rules.tsv -r baseline.html -z "$zap_options" || status=1
echo "== zap api scan (safe mode)"
zap zap-api-scan.py -t openapi.json -f openapi -O "$APP" -S -c rules.tsv -r api.html \
  -z "$zap_options" || status=1
[ "$status" = 0 ] && echo "OK: no ZAP finding above the levels in config/zap/rules.tsv"
exit "$status"
