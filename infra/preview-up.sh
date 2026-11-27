#!/usr/bin/env bash
# Makes sure the preview app of a pull request exists with its own database and its secrets
# (docs/plan.md, step 10). The deploy itself stays in ci.yml.
# Needs: FLY_API_TOKEN (org token), FLY_ORG, APP, DB, PREVIEW_LOGIN_NAME, PREVIEW_LOGIN_PASSWORD,
# PREVIEW_GITHUB_TOKEN.
set -euo pipefail
: "${FLY_ORG:?}" "${APP:?}" "${DB:?}" "${PREVIEW_LOGIN_NAME:?}" "${PREVIEW_LOGIN_PASSWORD:?}" "${PREVIEW_GITHUB_TOKEN:?}"

if ! flyctl status --app "$APP" > /dev/null 2>&1; then
  flyctl apps create "$APP" --org "$FLY_ORG"
  # Own database and user in the existing Postgres cluster; sets DATABASE_URL.
  flyctl postgres attach notes-fourteen-it-db --app "$APP" --database-name "$DB" --database-user "$DB" > /dev/null  # the output holds DATABASE_URL
  flyctl secrets set --app "$APP" --stage \
    SPRING_SECURITY_USER_NAME="$PREVIEW_LOGIN_NAME" SPRING_SECURITY_USER_PASSWORD="$PREVIEW_LOGIN_PASSWORD"
fi
# The token for the preview bar (comment and merge). Its own token, narrower than the one of
# production, because the preview runs code of the pull request. Set on every run, so that it
# reaches previews created before it existed and follows a rotated token.
flyctl secrets set --app "$APP" --stage GITHUB_TOKEN="$PREVIEW_GITHUB_TOKEN"
