#!/usr/bin/env bash
# Run Flyway migrations for dispatch-service and exit.
# Builds the jar if not already present, then starts the app on a random
# port solely to apply migrations, and shuts it down on completion.
#
# Usage: ./scripts/migrate-dispatch.sh
# Env vars (all have sane local defaults):
#   DISPATCH_DB_URL, DISPATCH_DB_USER, DISPATCH_DB_PASS, DISPATCH_SCHEMA
set -euo pipefail

JAVA_HOME="${JAVA_HOME:-${HOME}/.sdkman/candidates/java/25.0.2-graalce}"
DISPATCH_DB_URL="${DISPATCH_DB_URL:-jdbc:postgresql://localhost:5432/dispatch}"
DISPATCH_DB_USER="${DISPATCH_DB_USER:-dispatch}"
DISPATCH_DB_PASS="${DISPATCH_DB_PASS:-dispatch}"
DISPATCH_SCHEMA="${DISPATCH_SCHEMA:-dispatch}"
DISPATCH_JWT_PRIVATE_KEY_PEM="${DISPATCH_JWT_PRIVATE_KEY_PEM:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
JAR="$PROJECT_ROOT/dispatch-service/target/quarkus-app/quarkus-run.jar"
LOG_FILE="/tmp/dispatch-migrate-$$.log"

APP_PID=""
cleanup() {
    if [[ -n "$APP_PID" ]] && kill -0 "$APP_PID" 2>/dev/null; then
        kill "$APP_PID" 2>/dev/null || true
        wait "$APP_PID" 2>/dev/null || true
    fi
    rm -f "$LOG_FILE"
}
trap cleanup EXIT

if [[ ! -f "$JAR" ]]; then
    echo "Building dispatch-service jar..."
    JAVA_HOME="$JAVA_HOME" \
      mvn -pl dispatch-service -am package -DskipTests --no-transfer-progress \
          -f "$PROJECT_ROOT/pom.xml"
fi

echo "Running dispatch Flyway migrations against: $DISPATCH_DB_URL (schema: $DISPATCH_SCHEMA)"

DISPATCH_DB_URL="$DISPATCH_DB_URL" \
DISPATCH_DB_USER="$DISPATCH_DB_USER" \
DISPATCH_DB_PASS="$DISPATCH_DB_PASS" \
DISPATCH_SCHEMA="$DISPATCH_SCHEMA" \
DISPATCH_JWT_PRIVATE_KEY_PEM="$DISPATCH_JWT_PRIVATE_KEY_PEM" \
  "$JAVA_HOME/bin/java" \
    -Dquarkus.flyway.migrate-at-start=true \
    -Dquarkus.http.port=0 \
    -jar "$JAR" \
    > "$LOG_FILE" 2>&1 &
APP_PID=$!

TIMEOUT=120
ELAPSED=0
while [[ $ELAPSED -lt $TIMEOUT ]]; do
    if ! kill -0 "$APP_PID" 2>/dev/null; then
        echo "Application process exited unexpectedly"
        cat "$LOG_FILE"
        exit 1
    fi
    if grep -qE "Successfully applied|is up to date|No migration necessary" "$LOG_FILE" 2>/dev/null; then
        break
    fi
    sleep 2
    ELAPSED=$((ELAPSED + 2))
done

grep -E "Flyway|Migrat|flyway|dispatch-service started" "$LOG_FILE" 2>/dev/null | sed 's/\x1b\[[0-9;]*m//g' || true

if [[ $ELAPSED -ge $TIMEOUT ]]; then
    echo "ERROR: migrations did not complete within ${TIMEOUT}s"
    cat "$LOG_FILE"
    exit 1
fi

echo "Migrations complete."
