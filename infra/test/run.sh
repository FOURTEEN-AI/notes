#!/usr/bin/env bash
# Tests of the scripts in infra/ against fake flyctl, gh and curl (docs/plan.md, step 10): which
# calls a script makes for a given state of Fly, GitHub and IONOS. No secrets, no network.
# The answers are the JSON that the real tools gave when the scripts were written, so a change of
# their format is caught only by the integration test (infra/test/integration.sh).
set -uo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
failed=0

setup() {
  T=$(mktemp -d); CALLS=$T/calls; RESPONSES=$T/responses; : > "$CALLS"; : > "$RESPONSES"; n=0
  export CALLS RESPONSES REPORT=$T/report PATH="$root/infra/test/stubs:$PATH"
  export FLY_ORG=o APP=notes-test LOGIN_NAME=n LOGIN_PASSWORD=p APP_GITHUB_TOKEN=t DOMAIN=
}
# on <tool> <glob over the arguments> <output | FAIL>
on() { n=$((n + 1)); if [ "$3" = FAIL ]; then f=FAIL; else f=$T/r$n; printf '%s\n' "$3" > "$f"; fi; printf '%s\t%s\t%s\n' "$1" "$2" "$f" >> "$RESPONSES"; }
called() { grep -qE -- "$1" "$CALLS" || { echo "  expected a call: $1"; echo "  calls:"; sed 's/^/    /' "$CALLS"; return 1; }; }
not_called() { ! grep -qE -- "$1" "$CALLS" || { echo "  unexpected call: $1"; grep -E -- "$1" "$CALLS" | sed 's/^/    /'; return 1; }; }
writes='apps create|postgres create|machine update|machines destroy|postgres attach|certs add'

# A production that is as it should be.
healthy() {
  on flyctl 'apps list*' '[{"Name":"notes-test"},{"Name":"notes-test-db"}]'
  on flyctl 'machines list --app notes-test-db*' '[{"id":"m1","config":{"guest":{"memory_mb":512}}}]'
  on flyctl 'volumes list*' '[{"id":"vol1"}]'
  on flyctl 'secrets list*' '[{"Name":"DATABASE_URL"}]'
  on flyctl 'machines list --app notes-test *' '[{"id":"a","created_at":"2026-01-01T00:00:00Z"}]'
}

t_scripts_are_executable() { for f in "$root"/infra/*.sh "$root"/infra/test/*.sh; do [ -x "$f" ] || { echo "  not executable: $f"; return 1; }; done; }

t_preview_name() {
  [ "$("$root/infra/preview-name.sh" agent/issue-12 5)" = issue-12 ] && [ "$("$root/infra/preview-name.sh" feature/x 5)" = pr-5 ]
}

t_reconcile_changes_nothing_when_all_is_right() {
  healthy; "$root/infra/reconcile.sh" > /dev/null || return 1
  not_called "$writes" && [ ! -s "$REPORT" ] || { echo "  report: $(cat "$REPORT")"; return 1; }
}

t_reconcile_repairs_memory_and_extra_machine() {
  on flyctl 'apps list*' '[{"Name":"notes-test"},{"Name":"notes-test-db"}]'
  on flyctl 'machines list --app notes-test-db*' '[{"id":"m1","config":{"guest":{"memory_mb":256}}}]'
  on flyctl 'volumes list*' '[{"id":"vol1"}]'
  on flyctl 'secrets list*' '[{"Name":"DATABASE_URL"}]'
  on flyctl 'machines list --app notes-test --json' '[{"id":"b","created_at":"2026-02-01T00:00:00Z"},{"id":"a","created_at":"2026-01-01T00:00:00Z"}]'
  "$root/infra/reconcile.sh" > /dev/null || return 1
  called 'machine update m1 --app notes-test-db --vm-memory 512' && called 'machines destroy b --app notes-test --force' \
    && not_called 'machines destroy a ' && [ "$(wc -l < "$REPORT")" -eq 2 ]
}

t_reconcile_builds_from_nothing_and_does_not_print_the_database_url() {
  on flyctl 'apps list*' '[]'
  on flyctl 'machines list --app notes-test-db*' '[{"id":"m1","config":{"guest":{"memory_mb":256}}}]'
  on flyctl 'volumes list*' '[{"id":"vol1"}]'
  on flyctl 'secrets list*' '[]'
  on flyctl 'machines list --app notes-test --json' '[]'
  on flyctl 'postgres attach*' 'DATABASE_URL=postgres://user:secret@host:5432/db'
  on flyctl 'postgres create*' 'Username: postgres Password: secret'
  out=$("$root/infra/reconcile.sh" 2>&1) || { echo "$out"; return 1; }
  called 'apps create notes-test --org o' && called 'postgres create --name notes-test-db' && called 'postgres attach notes-test-db --app notes-test' \
    && called 'machine update m1' && called 'volumes update vol1 --app notes-test-db --scheduled-snapshots --snapshot-retention 30' \
    && called 'secrets set --app notes-test --stage' || return 1
  ! grep -q 'secret' <<< "$out" || { echo "  the output holds a secret: $out"; return 1; }
}

t_reconcile_stops_when_flyctl_fails_instead_of_creating() {
  on flyctl 'apps list*' FAIL
  "$root/infra/reconcile.sh" > /dev/null 2>&1 && { echo "  exited 0"; return 1; }
  not_called 'apps create|postgres create'
}

t_reconcile_adds_the_certificate() {
  healthy; on flyctl 'certs list*' '[]'; export DOMAIN=notes.example.com
  curl_ok=$T/bin; mkdir "$curl_ok"; printf '#!/bin/sh\n' > "$curl_ok/dns.sh"
  # dns.sh is not under test here: the fake curl answers a zone with the record in place
  on curl '*/dns/v1/zones' '[{"id":"z1","name":"example.com"}]'
  on curl '*/dns/v1/zones/z1' '{"records":[{"id":"r1","name":"notes.example.com","type":"CNAME","content":"notes-test.fly.dev."}]}'
  export IONOS_API_KEY=k
  "$root/infra/reconcile.sh" > /dev/null || return 1
  called 'certs add notes.example.com --app notes-test'
}

t_preview_down_removes_app_and_database() {
  on flyctl 'apps list*' '[{"Name":"notes-fourteen-it-pr-7"}]'
  APP=notes-fourteen-it-pr-7 DB=pr_7 "$root/infra/preview-down.sh" > /dev/null || return 1
  called 'apps destroy notes-fourteen-it-pr-7 --yes' && called 'drop database if exists pr_7'
}

t_preview_down_ignores_only_a_missing_app() {
  on flyctl 'apps list*' '[]'
  APP=notes-fourteen-it-pr-7 DB=pr_7 "$root/infra/preview-down.sh" > /dev/null || return 1
  not_called 'apps destroy' && called 'drop database if exists pr_7'
}

t_preview_down_fails_when_flyctl_fails() {
  on flyctl 'apps list*' FAIL
  APP=notes-fourteen-it-pr-7 DB=pr_7 "$root/infra/preview-down.sh" > /dev/null 2>&1 && { echo "  exited 0"; return 1; }
  not_called 'drop database'
}

t_preview_down_fails_when_the_destroy_fails() {
  on flyctl 'apps list*' '[{"Name":"notes-fourteen-it-pr-7"}]'; on flyctl 'apps destroy*' FAIL
  APP=notes-fourteen-it-pr-7 DB=pr_7 "$root/infra/preview-down.sh" > /dev/null 2>&1 && { echo "  exited 0"; return 1; }
  return 0
}

t_preview_down_refuses_other_names() {
  APP=notes-fourteen-it DB=pr_7 "$root/infra/preview-down.sh" > /dev/null 2>&1 && { echo "  removed production"; return 1; }
  APP=notes-fourteen-it-pr-7 DB=notes "$root/infra/preview-down.sh" > /dev/null 2>&1 && { echo "  dropped a database"; return 1; }
  not_called .
}

t_sweep_removes_only_previews_without_an_open_pull_request() {
  export GH_TOKEN=t GH_REPO=r/r
  on gh 'pr list*' $'agent/issue-7 12\nfeature/x 1'
  on flyctl 'apps list*' '[{"Name":"notes-fourteen-it"},{"Name":"notes-fourteen-it-db"},{"Name":"notes-fourteen-it-pr-1"},{"Name":"notes-fourteen-it-issue-7"},{"Name":"notes-fourteen-it-pr-9"}]'
  on flyctl '*select datname*' $'postgres\nnotes_fourteen_it\npr_1\nissue_7\npr_9\npr_3'
  "$root/infra/sweep.sh" > /dev/null 2>&1 || return 1
  called 'apps destroy notes-fourteen-it-pr-9 --yes' && called 'drop database if exists pr_9' && called 'drop database if exists pr_3' \
    && not_called 'apps destroy notes-fourteen-it(-pr-1|-issue-7|-db)? ' && not_called 'drop database if exists (pr_1|issue_7|notes)'
}

t_sweep_fails_when_flyctl_fails() {
  export GH_TOKEN=t GH_REPO=r/r
  on gh 'pr list*' 'feature/x 1'; on flyctl 'apps list*' '[]'; on flyctl 'ssh console*' FAIL
  "$root/infra/sweep.sh" > /dev/null 2>&1 && { echo "  exited 0"; return 1; }
  return 0
}

t_github_sets_a_missing_required_check_and_keeps_strict() {
  export GH_TOKEN=t GH_REPO=r/r
  on gh 'api repos/*required_status_checks' '{"strict":true,"contexts":["build","review"]}'
  out=$("$root/infra/github.sh") || return 1
  called 'api -X PATCH repos/r/r/branches/main/protection/required_status_checks --input -' && grep -q '"strict":true' "$CALLS.stdin" \
    && grep -q '"smells"' "$CALLS.stdin" && grep -q '^changed:' <<< "$out"
}

t_github_changes_nothing_when_the_checks_are_right() {
  export GH_TOKEN=t GH_REPO=r/r
  on gh 'api repos/*required_status_checks' "{\"strict\":false,\"contexts\":[\"smells\",\"build\",\"review\",\"acceptance\",\"secret-scan\",\"image-scan\"]}"
  out=$("$root/infra/github.sh") || return 1
  not_called 'PATCH' && [ -z "$out" ]
}

dns_env() { export IONOS_API_KEY=k DOMAIN=notes.example.com TARGET=notes-test.fly.dev GH_REPO=r/r GH_TOKEN=t; on curl '*/dns/v1/zones' '[{"id":"z1","name":"other.org"},{"id":"z2","name":"example.com"}]'; }
t_dns_creates_the_record() {
  dns_env; on curl '*/dns/v1/zones/z2' '{"records":[{"id":"x","name":"www.example.com","type":"A","content":"1.2.3.4"}]}'
  out=$("$root/infra/dns.sh") || return 1
  called '-X POST .*zones/z2/records -d \[\{"name":"notes.example.com","type":"CNAME","content":"notes-test.fly.dev"' && grep -q '^changed' <<< "$out"
}
t_dns_changes_a_wrong_record() {
  dns_env; on curl '*/dns/v1/zones/z2' '{"records":[{"id":"r1","name":"notes.example.com","type":"CNAME","content":"old.fly.dev."}]}'
  out=$("$root/infra/dns.sh") || return 1
  called '-X PUT .*zones/z2/records/r1 -d' && not_called 'POST' && grep -q '^changed' <<< "$out"
}
t_dns_leaves_a_right_record_alone() {
  dns_env; on curl '*/dns/v1/zones/z2' '{"records":[{"id":"r1","name":"notes.example.com","type":"CNAME","content":"notes-test.fly.dev."}]}'
  out=$("$root/infra/dns.sh") || return 1
  not_called '-X (POST|PUT|DELETE)' && [ -z "$out" ]
}
t_dns_refuses_another_record_type() {
  dns_env; on curl '*/dns/v1/zones/z2' '{"records":[{"id":"r1","name":"notes.example.com","type":"A","content":"1.2.3.4"}]}'
  "$root/infra/dns.sh" > /dev/null 2>&1 && { echo "  exited 0"; return 1; }
  not_called '-X (POST|PUT|DELETE)'
}
t_dns_stops_when_ionos_fails() {
  dns_env; : > "$RESPONSES"; on curl '*' FAIL
  "$root/infra/dns.sh" > /dev/null 2>&1 && { echo "  exited 0"; return 1; }
  not_called '-X'
}

restore_env() {
  on flyctl 'apps list*' '[{"Name":"notes-test-db"},{"Name":"notes-test-restore"}]'
  on flyctl 'volumes list*' '[{"id":"vol1"}]'
  on flyctl 'volumes snapshots list vol1 --app notes-test-db*' '[{"id":"old","status":"created","created_at":"2026-01-01T00:00:00Z"},{"id":"new","status":"created","created_at":"2026-02-01T00:00:00Z"},{"id":"pending","status":"running","created_at":"0001-01-01T00:00:00Z"}]'
}
t_restore_uses_the_latest_finished_snapshot_and_removes_the_scratch() {
  restore_env; on flyctl '*select count*' 3
  "$root/infra/restore-check.sh" > /dev/null || return 1
  called 'postgres create --name notes-test-restore .*--snapshot-id new' && called 'apps destroy notes-test-restore --yes'
}
t_restore_fails_on_an_empty_table_and_still_removes_the_scratch() {
  restore_env; on flyctl '*select count*' 0
  "$root/infra/restore-check.sh" > /dev/null 2>&1 && { echo "  exited 0"; return 1; }
  called 'apps destroy notes-test-restore --yes'
}
t_restore_fails_without_a_snapshot() {
  restore_env; : > "$RESPONSES"; on flyctl 'volumes list*' '[{"id":"vol1"}]'; on flyctl 'apps list*' '[{"Name":"notes-test-db"}]'; on flyctl 'volumes snapshots list*' '[]'
  "$root/infra/restore-check.sh" > /dev/null 2>&1 && { echo "  exited 0"; return 1; }
  not_called 'postgres create'
}

for t in $(declare -F | awk '{print $3}' | grep '^t_'); do
  setup
  if out=$( ( "$t" ) 2>&1 ); then echo "ok    $t"; else echo "FAIL  $t"; [ -z "$out" ] || sed 's/^/      /' <<< "$out"; failed=$((failed + 1)); fi
  rm -rf "$T"
done
[ "$failed" -eq 0 ] || { echo "$failed failed"; exit 1; }
