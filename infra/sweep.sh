#!/usr/bin/env bash
# Removes previews and preview databases whose pull request is not open any more
# (docs/plan.md, step 10). Prints what it removed.
# Needs: FLY_API_TOKEN (org token), FLY_ORG, GH_TOKEN, GH_REPO.
set -euo pipefail
: "${FLY_ORG:?}" "${GH_TOKEN:?}" "${GH_REPO:?}"
here=$(dirname "$0")

open=$(gh pr list --repo "$GH_REPO" --state open --limit 200 --json number,headRefName \
  --jq '.[] | "\(.headRefName) \(.number)"' | while read -r ref number; do "$here/preview-name.sh" "$ref" "$number"; done)

apps=$(flyctl apps list --org "$FLY_ORG" --json | jq -r '.[] | (.Name // .name)' \
  | sed -n 's/^notes-fourteen-it-\(\(issue\|pr\)-[0-9]\+\)$/\1/p')
dbs=$(flyctl ssh console --app notes-fourteen-it-db --command \
  "sh -c 'psql postgres://postgres:\$OPERATOR_PASSWORD@localhost:5432/postgres -Atc \"select datname from pg_database\"'" \
  | tr -d '\r' | grep -E '^(issue|pr)_[0-9]+$' | tr '_' '-' || true)

for n in $(printf '%s\n%s\n' "$apps" "$dbs" | sort -u | grep . || true); do
  if ! grep -qx "$n" <<< "$open"; then
    echo "swept preview $n"
    APP="notes-fourteen-it-$n" DB="${n//-/_}" "$here/preview-down.sh"
  fi
done
