#!/usr/bin/env bash
# Builds a copy of production next to it, breaks it, checks that reconcile.sh repairs it, restores a
# snapshot and removes the copy (docs/plan.md, step 10). Real Fly, real IONOS: the part of the test
# that the fakes of run.sh cannot give. With IONOS_API_KEY it also creates, breaks and repairs the
# DNS record of the copy and removes it again.
# Needs: FLY_API_TOKEN (org token) or a flyctl that is logged in, FLY_ORG. Takes a few minutes.
set -euo pipefail
cd "$(dirname "$0")/../.."
source infra/lib.sh
: "${FLY_ORG:?}"
export APP=${APP:-notes-infratest-${GITHUB_RUN_ID:-$RANDOM$RANDOM}}
export LOGIN_NAME=test LOGIN_PASSWORD=test-not-real APP_GITHUB_TOKEN=test-not-real
export DOMAIN= REPORT=$(mktemp)
zone=fourteen-it.de
dns=https://api.hosting.ionos.com/dns/v1
ionos() { curl -fsS -H "X-API-Key: $IONOS_API_KEY" -H "Content-Type: application/json" "$@"; }
zone_id() { ionos "$dns/zones" | jq -r --arg z "$zone" '.[] | select(.name == $z) | .id'; }
record() { ionos "$dns/zones/$(zone_id)" | jq -c --arg d "$APP.$zone" '[.records[]? | select(.name == $d)] | first // empty'; }

destroy() { if app_exists "$1"; then flyctl apps destroy "$1" --yes; fi; }
cleanup() {
  status=$?
  set +e
  if [ -n "${IONOS_API_KEY:-}" ] && id=$(record | jq -r '.id // empty') && [ -n "$id" ]; then ionos -X DELETE "$dns/zones/$(zone_id)/records/$id"; fi
  destroy "$APP-restore"; destroy "$APP"; destroy "$APP-db"
  [ $status -eq 0 ] && echo "integration test passed" || echo "integration test FAILED"
  exit $status
}
trap cleanup EXIT
fail() { echo "FAIL: $*" >&2; echo "report:" >&2; cat "$REPORT" >&2; exit 1; }
expect_report() { grep -q -- "$1" "$REPORT" || fail "the report has no line matching: $1"; }
reconcile() { : > "$REPORT"; echo "::group::reconcile $*"; infra/reconcile.sh; echo "::endgroup::"; }
db_machine() { flyctl machines list --app "$APP-db" --json | jq -r 'first | .id'; }
db_memory() { flyctl machines list --app "$APP-db" --json | jq -r 'first | .config.guest.memory_mb'; }
psql_in() { flyctl ssh console --app "$APP-db" --command "sh -c 'psql postgres://postgres:\$OPERATOR_PASSWORD@localhost:5432/${APP//-/_} -c \"$1\"'"; }

# Leftovers of a run that died before its cleanup.
for old in $(flyctl apps list --org "$FLY_ORG" --json | jq -r '.[] | (.Name // .name)' | grep -E '^notes-infratest-[0-9]+(-db|-restore)?$' || true); do
  [ "$old" = "$APP" ] || [ "$old" = "$APP-db" ] || [ "$old" = "$APP-restore" ] || { echo "removing leftover $old"; flyctl apps destroy "$old" --yes; }
done

echo "== 1. from nothing"
reconcile
expect_report 'created app'; expect_report 'created Postgres'; expect_report 'attached'
app_exists "$APP" && app_exists "$APP-db" || fail "app or database missing"
[ "$(db_memory)" = 512 ] || fail "memory of the database is $(db_memory)"

echo "== 2. a second run changes nothing"
reconcile
[ ! -s "$REPORT" ] || fail "the second run changed something"

echo "== 3. drift is repaired"
flyctl machine update "$(db_machine)" --app "$APP-db" --vm-memory 256 --yes > /dev/null
for _ in 1 2; do flyctl machine run nginx:alpine --app "$APP" --region fra --vm-memory 256 > /dev/null; done
[ "$(flyctl machines list --app "$APP" --json | jq length)" -eq 2 ] || fail "no second machine to repair"
reconcile
expect_report 'memory of'; expect_report 'extra machine'
[ "$(db_memory)" = 512 ] || fail "memory of the database is $(db_memory)"
[ "$(flyctl machines list --app "$APP" --json | jq length)" -eq 1 ] || fail "more than one machine left"
reconcile
[ ! -s "$REPORT" ] || fail "the run after the repair changed something"

echo "== 4. a snapshot can be restored"
psql_in "create table if not exists note (id int, text text)" > /dev/null
psql_in "insert into note values (1, chr(120))" > /dev/null
volume=$(flyctl volumes list --app "$APP-db" --json | jq -r 'first | .id')
flyctl volumes snapshots create "$volume" --app "$APP-db" > /dev/null
for _ in $(seq 30); do
  [ "$(flyctl volumes snapshots list "$volume" --app "$APP-db" --json | jq '[.[] | select(.status == "created")] | length')" -gt 0 ] && break
  sleep 10
done
infra/restore-check.sh
app_exists "$APP-restore" && fail "the scratch database is still there"

if [ -n "${IONOS_API_KEY:-}" ]; then
  echo "== 5. the DNS record and the certificate"
  export DOMAIN=$APP.$zone
  reconcile
  expect_report 'created CNAME'; expect_report 'added the certificate'
  [ "$(record | jq -r '.content | rtrimstr(".")')" = "$APP.fly.dev" ] || fail "wrong record: $(record)"
  ionos -X PUT "$dns/zones/$(zone_id)/records/$(record | jq -r .id)" \
    -d '{"content":"wrong.example.com","ttl":3600,"prio":0,"disabled":false}' > /dev/null
  reconcile
  expect_report 'pointed to wrong.example.com'
  [ "$(record | jq -r '.content | rtrimstr(".")')" = "$APP.fly.dev" ] || fail "record not repaired: $(record)"
  reconcile
  [ ! -s "$REPORT" ] || fail "the run after the DNS repair changed something"
else
  echo "== 5. skipped (no IONOS_API_KEY)"
fi
