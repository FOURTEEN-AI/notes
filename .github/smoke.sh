#!/usr/bin/env bash
# Smoke test of a deployed app: usage `.github/smoke.sh <app>` (the host is <app>.fly.dev).
# Polls /actuator/health until "status":"UP" (a stopped machine starts on the first request),
# then checks that / answers 401 without login. Only GETs, no secrets.
set -u
url="https://$1.fly.dev"
for i in $(seq 1 30); do
  body=$(curl -s --max-time 10 "$url/actuator/health" || true)
  if grep -q '"status":"UP"' <<< "$body"; then
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$url/")
    [ "$code" = 401 ] && { echo "OK: $url is UP and / answers 401"; exit 0; }
    echo "FAIL: / without login answered $code, expected 401"; exit 1
  fi
  echo "waiting for $url ($i/30)"; sleep 5
done
echo "FAIL: $url/actuator/health is not UP after 30 tries"; exit 1
