#!/bin/bash
set -euo pipefail

echo "=== Moving Minecraft source patches back ==="

DEST_DIR="canvas-server/minecraft-patches/sources"
SRC_DIR="canvas-server/minecraft-patches/sources_unapplied"

if [[ -d "$SRC_DIR" ]]; then
  mkdir -p "$DEST_DIR"
  mv "$SRC_DIR"/* "$DEST_DIR"/ 2>/dev/null || true
fi

echo "=== Applying Minecraft source patches and moving failed to rejected ==="
./gradlew applyOrMoveMinecraftSourcePatches --quiet

echo "=== Disabling git file patches ==="

BUILD_FILE="canvas-server/build.gradle.kts"

if [[ ! -f "$BUILD_FILE" ]]; then
  echo "ERROR: $BUILD_FILE not found"
  exit 1
fi

sed -i 's/gitFilePatches *= *true/gitFilePatches = false/' "$BUILD_FILE"

echo "=== Rebuilding single file patches ==="
./gradlew rebuildFoliaSingleFilePatches --quiet

echo "=== Rebuilding applied Minecraft source patches as per-file patches ==="
./gradlew rebuildMinecraftSourcePatches --quiet

echo "=== Pushing to Patch Roulette ==="
./gradlew canvasPatchRoulettePush -Dpaperweight.debug=true --quiet

echo "=== DONE ==="