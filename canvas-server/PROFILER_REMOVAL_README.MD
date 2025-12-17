The script used to create the profiler removal patch is as such:

```bash
#!/usr/bin/env bash
set -euo pipefail

PATCH_NAME="0007-Region-profiler.patch"
COMMIT_MESSAGE="Remove Folia Profiler"

ROOT_DIR="$(pwd)"
JAVA_SRC_DIR="folia-server/src/minecraft/java"
PATCH_DIR="folia-server/minecraft-patches/features"

if [[ ! -d "$JAVA_SRC_DIR" ]]; then
  echo "Error: Expected directory '$JAVA_SRC_DIR' does not exist."
  exit 1
fi

if [[ ! -d "$PATCH_DIR" ]]; then
  echo "Error: Expected patch directory '$PATCH_DIR' does not exist."
  exit 1
fi

PATCH_PATH="$PATCH_DIR/$PATCH_NAME"

if [[ ! -f "$PATCH_PATH" ]]; then
  echo "Error: Patch file '$PATCH_NAME' not found in $PATCH_DIR"
  exit 1
fi

echo "Setting up dev environment..."
./gradlew applyAllPatches

cd "$JAVA_SRC_DIR"

echo "Reversing patch: $PATCH_PATH"
patch -p1 -R < "$ROOT_DIR/$PATCH_PATH"

echo "Staging changes..."
git add .

echo "Committing changes..."
git commit -m "$COMMIT_MESSAGE"

cd "$ROOT_DIR"

echo "Rebuilding Minecraft feature patches..."
./gradlew rebuildMinecraftFeaturePatches

echo "Done. Please be sure to trim orig files and fix the commit description"
```

The output file used in production is modified from its original form by applying the following modifications:
- Removal of the diff of adding TickRegionScheduler.java.orig
- Addition of this commit description
