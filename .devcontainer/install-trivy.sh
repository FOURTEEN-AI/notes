#!/usr/bin/env bash
# Installs Trivy into the directory given as argument (default ~/.local/bin). The one version is
# used by the `image-scan` job and the nightly scan in CI and can be run locally in the
# devcontainer. The checksums are pinned here, not read from the release, so a replaced release
# asset fails the install.
set -euo pipefail

version=0.76.2
dir="${1:-$HOME/.local/bin}"

case "$(uname -m)" in
  x86_64) arch=64bit sha=bc0fc726e017b8927a62b2d3531d7a84ee01769daf5c99ed866dced54089fdbf ;;
  aarch64 | arm64) arch=ARM64 sha=ab9d51f86e6a5fb936f605faff6b593ac51bc378fff4d3e8b9d58358b9c367c3 ;;
  *) echo "unsupported architecture $(uname -m)" >&2; exit 1 ;;
esac

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
curl -fsSL -o "$tmp/trivy.tar.gz" \
  "https://github.com/aquasecurity/trivy/releases/download/v$version/trivy_${version}_Linux-$arch.tar.gz"
echo "$sha  $tmp/trivy.tar.gz" | sha256sum -c -
mkdir -p "$dir"
tar -xz -C "$dir" -f "$tmp/trivy.tar.gz" trivy
