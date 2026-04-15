#!/bin/bash
set -euo pipefail

echo "=== Applying all patches ==="
./gradlew applyAllPatches --quiet

echo "=== Enabling git file patches ==="

BUILD_FILE="canvas-server/build.gradle.kts"

if [[ ! -f "$BUILD_FILE" ]]; then
  echo "ERROR: $BUILD_FILE not found"
  exit 1
fi

sed -i 's/gitFilePatches *= *false/gitFilePatches = true/' "$BUILD_FILE"

echo "=== Rebuilding single file patches ==="
./gradlew rebuildFoliaSingleFilePatches --quiet

echo "=== Rebuilding Minecraft source patches as Git patches ==="
./gradlew rebuildMinecraftSourcePatches --quiet

echo "=== Moving Minecraft source patches to sources_unapplied ==="

SRC_DIR="canvas-server/minecraft-patches/sources"
DEST_DIR="canvas-server/minecraft-patches/sources_unapplied"

if [[ -d "$SRC_DIR" ]]; then
  mkdir -p "$DEST_DIR"
  mv "$SRC_DIR"/* "$DEST_DIR"/ 2>/dev/null || true
fi

echo "=== DONE ==="