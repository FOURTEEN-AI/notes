#!/usr/bin/env bash
# Removes a preview app and its database (docs/plan.md, step 10). Only "does not exist" is fine;
# any other failure fails the run.
# Needs: FLY_API_TOKEN (org token), FLY_ORG, APP, DB.
set -euo pipefail
source "$(dirname "$0")/lib.sh"
: "${FLY_ORG:?}" "${APP:?}" "${DB:?}"
[[ "$APP" =~ ^notes-fourteen-it-(issue|pr)-[0-9]+$ ]] || { echo "refusing to remove $APP" >&2; exit 1; }
[[ "$DB" =~ ^(issue|pr)_[0-9]+$ ]] || { echo "refusing to drop $DB" >&2; exit 1; }

if app_exists "$APP"; then
  flyctl apps destroy "$APP" --yes
fi
flyctl ssh console --app notes-fourteen-it-db --command \
  "sh -c 'psql postgres://postgres:\$OPERATOR_PASSWORD@localhost:5432/postgres -c \"drop database if exists $DB with (force)\" -c \"drop role if exists $DB\"'"
