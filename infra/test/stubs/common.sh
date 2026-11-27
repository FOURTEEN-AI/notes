#!/usr/bin/env bash
# The body of every fake tool (docs/plan.md, step 10). Logs the call to $CALLS and answers from the
# table $RESPONSES: lines "tool<TAB>glob over the arguments<TAB>file or FAIL"; the first match wins.
# No match: no output, success (a write).
tool=$1; shift
echo "$tool $*" >> "$CALLS"
for a in "$@"; do [ "$a" = --input ] && cat >> "$CALLS.stdin"; done
while IFS=$'\t' read -r t pattern file; do
  [ "$t" = "$tool" ] || continue
  # shellcheck disable=SC2053
  if [[ "$*" == $pattern ]]; then
    [ "$file" = FAIL ] && exit 1
    cat "$file"; exit 0
  fi
done < "$RESPONSES"
