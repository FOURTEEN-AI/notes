#!/usr/bin/env bash
# Installs what the devcontainer features don't: the Node version pinned in
# .node-version, pnpm and flyctl.
set -eo pipefail

# Node: switch the node feature's nvm to the pinned version. Global packages
# installed by features (Claude Code) are carried over to it.
source "$NVM_DIR/nvm.sh"
wanted="$(tr -d '[:space:]' < .node-version)"
if [ "$(nvm version "$wanted")" = "N/A" ]; then
  nvm install "$wanted" --reinstall-packages-from=current
fi
nvm alias default "$wanted"
nvm use default

# pnpm: any version will do; it switches to the one pinned in package.json's
# packageManager field on its own.
npm install -g pnpm

# flyctl: Fly's official installer, into ~/.fly/bin (on PATH via remoteEnv).
curl -fsSL https://fly.io/install.sh | sh
