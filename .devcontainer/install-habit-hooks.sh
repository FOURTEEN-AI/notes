#!/usr/bin/env bash
# Installs habit-hooks (docs/plan.md, step 9) into the directory given as argument (default
# ~/.local/bin): the commands habit-hooks, habit-sensors and habit-mapper, and pmd, the detector for
# Java. The detectors for the frontend (ESLint, knip) are those of `frontend/node_modules`. The one
# version is used by the `smells` job in CI, by the coding agent and in the devcontainer. Everything
# is pinned with a checksum here, not read from a release or an index: uv and PMD by the checksums
# below, the Python packages by the hashes in habit-hooks-requirements.txt (--require-hashes).
set -euo pipefail

uv_version=0.13.10
pmd_version=7.29.0
pmd_sha=d27177c185b837fa57f097e482d1253468aa035379c5eb2671fa0a279109b179
dir="${1:-$HOME/.local/bin}"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

case "$(uname -m)" in
  x86_64) arch=x86_64 uv_sha=bf65ac1a0af10f92c522d91ac096c7c0a39657647b155577e7836c2cbbb4526c ;;
  aarch64 | arm64) arch=aarch64 uv_sha=14590f644805a6234ffe071154340669e38137f38e60aa9629690119704107d2 ;;
  *) echo "unsupported architecture $(uname -m)" >&2; exit 1 ;;
esac

dir="$(mkdir -p "$dir" && cd "$dir" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

curl -fsSL -o "$tmp/uv.tar.gz" \
  "https://github.com/astral-sh/uv/releases/download/$uv_version/uv-$arch-unknown-linux-gnu.tar.gz"
echo "$uv_sha  $tmp/uv.tar.gz" | sha256sum -c -
tar -xz -C "$tmp" -f "$tmp/uv.tar.gz"
uv="$tmp/uv-$arch-unknown-linux-gnu/uv"

rm -rf "$dir/habit-hooks-venv"
"$uv" venv --quiet "$dir/habit-hooks-venv"
"$uv" pip install --quiet --python "$dir/habit-hooks-venv/bin/python" --require-hashes \
  -r "$here/habit-hooks-requirements.txt"
for tool in habit-hooks habit-sensors habit-mapper; do
  ln -sf "$dir/habit-hooks-venv/bin/$tool" "$dir/$tool"
done

curl -fsSL -o "$tmp/pmd.zip" \
  "https://github.com/pmd/pmd/releases/download/pmd_releases%2F$pmd_version/pmd-dist-$pmd_version-bin.zip"
echo "$pmd_sha  $tmp/pmd.zip" | sha256sum -c -
rm -rf "$dir/pmd-dist"
unzip -q "$tmp/pmd.zip" -d "$dir/pmd-dist"
ln -sf "$dir/pmd-dist/pmd-bin-$pmd_version/bin/pmd" "$dir/pmd"
