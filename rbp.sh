#!/bin/bash

# If running with old Bash (3.x), try Homebrew Bash on macOS first.
if [ -z "${BASH_VERSINFO:-}" ] || [ "${BASH_VERSINFO[0]}" -lt 4 ]; then
  if [ "$(uname -s)" = "Darwin" ]; then
    if command -v /opt/homebrew/bin/bash >/dev/null 2>&1; then
      exec /opt/homebrew/bin/bash "$0" "$@"
    elif command -v /usr/local/bin/bash >/dev/null 2>&1; then
      exec /usr/local/bin/bash "$0" "$@"
    else
      echo "Error: Bash 4+ required but not found."
      echo "Install with: brew install bash"
      exit 1
    fi
  fi

  echo "Error: Bash 4+ required."
  exit 1
fi

set -e

force_run=false
gradle_run=false
debug=false

for arg in "$@"; do
  case "$arg" in
    --force)
      force_run=true
      echo "Force mode enabled. All Gradle tasks will run."
      ;;
    --gradle)
      gradle_run=true
      echo "--gradle flag detected. Will run single file patch rebuild."
      ;;
    --debug)
      debug=true
      echo "Enabled debug logs"
      ;;
  esac
done

declare -A gradle_tasks

process_changes() {
  local dir="$1"
  local project="$2"

  if [ ! -d "$dir" ]; then
    echo "err: The directory '$dir' does not exist or is not valid."
    exit 1
  fi

  cd "$dir"

  if $force_run || ! git diff --quiet || ! git diff --cached --quiet; then
    gradle_tasks["fixup${project}FilePatches"]="true"
    gradle_tasks["rebuild${project}FilePatches"]="true"
  fi

  cd - > /dev/null
}

run_gradle_task() {
  local task="$1"
  local extra=""
  [[ "$debug" == "true" ]] && extra="-Dpaperweight.debug=true"
 if [[ "${gradle_tasks[$task]}" == "true" ]]; then
    if [[ -n "$extra" ]]; then
      ./gradlew "$task" $extra || { echo "Gradle task '$task' failed."; }
    else
      ./gradlew "$task" || { echo "Gradle task '$task' failed."; }
    fi
  fi
}

process_changes "./paper-server/" "PaperServer"
process_changes "./paper-api/" "PaperApi"
process_changes "./canvas-server/src/minecraft/java" "Minecraft"

gradle_rebuild_task=false

if $gradle_run || ! git diff --quiet "./canvas-server/build.gradle.kts" || ! git diff --cached --quiet "./canvas-server/build.gradle.kts"; then
  gradle_rebuild_task=true
fi

if $gradle_run || ! git diff --quiet "./canvas-api/build.gradle.kts" || ! git diff --cached --quiet "./canvas-api/build.gradle.kts"; then
  gradle_rebuild_task=true
fi

if $gradle_rebuild_task || $gradle_run; then
  gradle_tasks["rebuildPaperSingleFilePatches"]="true"
fi

echo "running fixup"
run_gradle_task "fixupPaperApiFilePatches"
run_gradle_task "fixupPaperServerFilePatches"
run_gradle_task "fixupMinecraftFilePatches"

echo "rebuilding"
run_gradle_task "rebuildPaperApiFilePatches"
run_gradle_task "rebuildPaperServerFilePatches"
run_gradle_task "rebuildMinecraftFilePatches"
run_gradle_task "rebuildPaperSingleFilePatches"

echo "done :)"
