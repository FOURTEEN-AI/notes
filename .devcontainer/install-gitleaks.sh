#!/usr/bin/env bash
# Installs gitleaks into the directory given as argument (default ~/.local/bin). The one version
# is used by the pre-commit hook in the devcontainer and by the `secrets` job in CI.
set -euo pipefail

version=8.28.0
dir="${1:-$HOME/.local/bin}"

case "$(uname -m)" in
  x86_64) arch=x64 ;;
  aarch64 | arm64) arch=arm64 ;;
  *) echo "unsupported architecture $(uname -m)" >&2; exit 1 ;;
esac

mkdir -p "$dir"
curl -fsSL "https://github.com/gitleaks/gitleaks/releases/download/v$version/gitleaks_${version}_linux_$arch.tar.gz" \
  | tar -xz -C "$dir" gitleaks
