#!/bin/bash

set -euo pipefail

TIMESTAMP_FILE=".jenkins/lastRunTimestamp"
mkdir -p .jenkins

NOW=$(date +%s)
SUNDAY_MIDNIGHT=$(date -d "last sunday 00:00" +%s)

if [[ -f "$TIMESTAMP_FILE" ]]; then
    LAST_RUN=$(date -d "$(cat "$TIMESTAMP_FILE")" +%s)
    if [[ $LAST_RUN -ge $SUNDAY_MIDNIGHT ]]; then
        echo "Skipping publish: already run this week (since Sunday midnight)."
        exit 0
    fi
fi

echo "Attempting to run publishAllPublicationsToCanvasmcRepository..."

if ./gradlew publishAllPublicationsToCanvasmcRepository; then
    echo "Task succeeded."
else
    echo "Task failed, but will still update the timestamp to avoid retry this week."
fi

date -Iseconds > "$TIMESTAMP_FILE"
echo "Timestamp updated to $(cat "$TIMESTAMP_FILE")"
