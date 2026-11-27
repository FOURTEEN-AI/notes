#!/usr/bin/env bash
# The required checks of the branch protection of main and the Dependabot security updates, from
# the list below (docs/plan.md, step 10). A new check is added here, in the commit that adds it.
# Needs: GH_TOKEN (administration: write), GH_REPO. Prints "changed: ..." per change.
set -euo pipefail
: "${GH_TOKEN:?}" "${GH_REPO:?}"
checks='["acceptance","build","image-scan","review","secret-scan","smells"]'

current=$(gh api "repos/$GH_REPO/branches/main/protection/required_status_checks")
if [ "$(jq -c '.contexts | sort' <<< "$current")" != "$checks" ]; then
  jq -n --argjson c "$checks" --argjson s "$(jq '.strict' <<< "$current")" '{strict: $s, contexts: $c}' \
    | gh api -X PATCH "repos/$GH_REPO/branches/main/protection/required_status_checks" --input - > /dev/null
  echo "changed: required checks of main are now $checks"
fi
# Both calls do nothing when the setting is already on.
gh api -X PUT "repos/$GH_REPO/vulnerability-alerts" > /dev/null
gh api -X PUT "repos/$GH_REPO/automated-security-fixes" > /dev/null
