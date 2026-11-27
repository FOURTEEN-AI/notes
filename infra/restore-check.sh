#!/usr/bin/env bash
# Restores the latest snapshot of the database volume into a scratch Postgres, counts the notes
# and removes it again (docs/plan.md, step 10). Fails if the restore does not work or the table
# is empty.
# Needs: FLY_API_TOKEN (org token), FLY_ORG. Parameters: APP.
set -euo pipefail
source "$(dirname "$0")/lib.sh"
: "${FLY_ORG:?}"
APP=${APP:-notes-fourteen-it}
DB_APP=$APP-db
SCRATCH=$APP-restore
REGION=${REGION:-fra}
cleanup() { if app_exists "$SCRATCH"; then flyctl apps destroy "$SCRATCH" --yes; fi; }
trap cleanup EXIT
cleanup

volume=$(flyctl volumes list --app "$DB_APP" --json | jq -r 'first | .id')
snapshot=$(flyctl volumes snapshots list "$volume" --app "$DB_APP" --json | jq -r '[.[] | select(.status == "created")] | sort_by(.created_at) | last | .id // empty')
[ -n "$snapshot" ] || { echo "no snapshot of $volume" >&2; exit 1; }

flyctl postgres create --name "$SCRATCH" --org "$FLY_ORG" --region "$REGION" --initial-cluster-size 1 \
  --vm-size shared-cpu-1x --volume-size 1 --snapshot-id "$snapshot" > /dev/null
db=${APP//-/_}
count=$(flyctl ssh console --app "$SCRATCH" --command \
  "sh -c 'psql postgres://postgres:\$OPERATOR_PASSWORD@localhost:5432/$db -Atc \"select count(*) from note\"'" \
  | tr -d '\r' | tail -1)
echo "restored $snapshot: $count notes"
[[ "$count" =~ ^[0-9]+$ ]] && [ "$count" -gt 0 ]
