#!/usr/bin/env bash
# Builds production from nothing and repairs what drifted (docs/plan.md, step 10). Every step is
# "ensure X" and safe to run again. Appends one line per change to REPORT.
# Needs: FLY_API_TOKEN (org token), FLY_ORG, LOGIN_NAME, LOGIN_PASSWORD, APP_GITHUB_TOKEN,
# IONOS_API_KEY. Parameters, so that a copy can be built next to production: APP, DOMAIN
# (empty = no DNS and certificate), REPORT.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${FLY_ORG:?}" "${LOGIN_NAME:?}" "${LOGIN_PASSWORD:?}" "${APP_GITHUB_TOKEN:?}"
APP=${APP:-notes-fourteen-it}
DB_APP=$APP-db
DOMAIN=${DOMAIN-notes.fourteen-it.de}
REGION=${REGION:-fra}
REPORT=${REPORT:-/dev/null}
changed() { echo "$*"; echo "- $*" >> "$REPORT"; }
secret_names() { flyctl secrets list --app "$1" --json | jq -r '.[] | (.Name // .name)'; }

# 1. The app.
if ! flyctl apps list --org "$FLY_ORG" --json | jq -e --arg a "$APP" 'any(.[]; (.Name // .name) == $a)' > /dev/null; then
  flyctl apps create "$APP" --org "$FLY_ORG"
  changed "created app $APP"
fi

# 2. The Postgres cluster: one small machine, 1 GB volume, 512 MB RAM, snapshots for 30 days.
# The output holds credentials, so it is dropped; the app gets its own user in step 3.
if ! flyctl apps list --org "$FLY_ORG" --json | jq -e --arg a "$DB_APP" 'any(.[]; (.Name // .name) == $a)' > /dev/null; then
  flyctl postgres create --name "$DB_APP" --org "$FLY_ORG" --region "$REGION" \
    --initial-cluster-size 1 --vm-size shared-cpu-1x --volume-size 1 > /dev/null
  changed "created Postgres $DB_APP"
fi
machine=$(flyctl machines list --app "$DB_APP" --json | jq -r 'first | .id')
if [ "$(flyctl machines list --app "$DB_APP" --json | jq -r 'first | .config.guest.memory_mb')" != 512 ]; then
  flyctl machine update "$machine" --app "$DB_APP" --vm-memory 512 --yes
  changed "set the memory of $DB_APP to 512 MB"
fi
volume=$(flyctl volumes list --app "$DB_APP" --json | jq -r 'first | .id')
flyctl volumes update "$volume" --app "$DB_APP" --scheduled-snapshots --snapshot-retention 30 > /dev/null

# 3. The connection and the secrets. GitHub is the one place a secret is changed; staged secrets
# take effect with the next deploy.
if ! secret_names "$APP" | grep -qx DATABASE_URL; then
  flyctl postgres attach "$DB_APP" --app "$APP" > /dev/null  # the output holds DATABASE_URL
  changed "attached $APP to $DB_APP"
fi
flyctl secrets set --app "$APP" --stage \
  SPRING_SECURITY_USER_NAME="$LOGIN_NAME" SPRING_SECURITY_USER_PASSWORD="$LOGIN_PASSWORD" \
  GITHUB_TOKEN="$APP_GITHUB_TOKEN" > /dev/null

# 4. The DNS record and the certificate.
if [ -n "$DOMAIN" ]; then
  out=$(DOMAIN="$DOMAIN" TARGET="$APP.fly.dev" infra/dns.sh)
  [ -z "$out" ] || changed "$out"
  if ! flyctl certs list --app "$APP" --json | jq -e --arg d "$DOMAIN" 'any(.[]; (.hostname // .Hostname) == $d)' > /dev/null; then
    flyctl certs add "$DOMAIN" --app "$APP"
    changed "added the certificate for $DOMAIN"
  fi
fi

# 5. Exactly one app machine: the first deploy with --ha=false creates two. The oldest stays.
# Runs in the concurrency group of the deploy, so no rolling deploy is under way.
ids=$(flyctl machines list --app "$APP" --json | jq -r 'sort_by(.created_at) | .[1:][] | .id')
for id in $ids; do
  flyctl machines destroy "$id" --app "$APP" --force
  changed "destroyed the extra machine $id of $APP"
done
