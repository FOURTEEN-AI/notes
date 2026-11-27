#!/usr/bin/env bash
# Makes sure that DOMAIN is a CNAME to TARGET at IONOS (hosting DNS API) and leaves every other
# record alone (docs/plan.md, step 10). Prints "changed: ..." when it had to write.
# Needs: IONOS_API_KEY (<prefix>.<secret>), DOMAIN, TARGET.
set -euo pipefail
: "${IONOS_API_KEY:?}" "${DOMAIN:?}" "${TARGET:?}"
api=https://api.hosting.ionos.com/dns/v1
call() { curl -fsS -H "X-API-Key: $IONOS_API_KEY" -H "Content-Type: application/json" "$@"; }

# The zone is the domain without its first label (notes.fourteen-it.de -> fourteen-it.de).
zone_name=${DOMAIN#*.}
zone_id=$(call "$api/zones" | jq -r --arg z "$zone_name" '[.[] | select(.name == $z)] | first | .id // empty')
[ -n "$zone_id" ] || { echo "no zone $zone_name at IONOS" >&2; exit 1; }

records=$(call "$api/zones/$zone_id" | jq -c --arg d "$DOMAIN" '[.records[]? | select(.name == $d)]')
if jq -e '[.[] | select(.type != "CNAME")] | length > 0' <<< "$records" > /dev/null; then
  echo "$DOMAIN has a record that is not a CNAME; not touching it" >&2
  exit 1
fi
id=$(jq -r 'first | .id // empty' <<< "$records")
content=$(jq -r 'first | .content // empty' <<< "$records")
body=$(jq -cn --arg n "$DOMAIN" --arg c "$TARGET" '{name: $n, type: "CNAME", content: $c, ttl: 3600, prio: 0, disabled: false}')

if [ -z "$id" ]; then
  call -X POST "$api/zones/$zone_id/records" -d "[$body]" > /dev/null
  echo "changed: created CNAME $DOMAIN -> $TARGET"
elif [ "${content%.}" != "${TARGET%.}" ]; then
  call -X PUT "$api/zones/$zone_id/records/$id" -d "$(jq -c 'del(.name, .type)' <<< "$body")" > /dev/null
  echo "changed: CNAME $DOMAIN pointed to $content, now $TARGET"
fi
