#!/usr/bin/env bash
# Bootstrap the pinned, checksum-verified Gradle distribution before building.
# The checked-in Gradle 8.14.6 wrapper JAR has no built-in download retries.
set -euo pipefail

max_attempts=4
for ((attempt = 1; attempt <= max_attempts; attempt++)); do
    if ./gradlew --version; then
        exit 0
    fi
    if ((attempt == max_attempts)); then
        echo "Gradle wrapper bootstrap failed after ${max_attempts} attempts." >&2
        exit 1
    fi
    delay=$((attempt * 15))
    echo "Gradle distribution download failed (attempt ${attempt}/${max_attempts}); retrying in ${delay}s." >&2
    sleep "${delay}"
done
