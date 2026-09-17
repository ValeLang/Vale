#!/usr/bin/env bash
# Clone the Sylvan/Vale repo plus its submodules (Guardian, Luz).
#
# Usage: scripts/recreate-setup.sh [target-dir] [branch]
# Defaults: target-dir=./Sylvan, branch=master

set -euo pipefail

TARGET_DIR="${1:-$PWD/Sylvan}"
BRANCH="${2:-master}"
REPO_URL="https://github.com/Verdagon/Vale"

if [[ -d "$TARGET_DIR/.git" ]]; then
  echo "Existing repo at $TARGET_DIR -- updating."
  git -C "$TARGET_DIR" fetch --all --tags
  git -C "$TARGET_DIR" submodule update --init --recursive
else
  git clone --branch "$BRANCH" --recurse-submodules "$REPO_URL" "$TARGET_DIR"
fi

echo "Done. Repo at: $TARGET_DIR"
echo "Submodules:"
git -C "$TARGET_DIR" submodule status
