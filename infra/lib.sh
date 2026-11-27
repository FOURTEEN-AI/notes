# shellcheck shell=bash
# Shared by the scripts in infra/ (docs/plan.md, step 10). Source it after `set -euo pipefail`.

# app_exists <app>: succeeds if the app is in the org. Stops the script when flyctl itself fails: a
# pipe into jq inside an `if` would take a failing flyctl for "does not exist" and go on.
app_exists() {
  local json
  json=$(flyctl apps list --org "$FLY_ORG" --json) || { echo "flyctl apps list failed" >&2; exit 1; }
  jq -e --arg a "$1" 'any(.[]; (.Name // .name) == $a)' <<< "$json" > /dev/null
}
