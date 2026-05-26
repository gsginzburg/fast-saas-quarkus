#!/usr/bin/env bash
# Bootstrap local PostgreSQL databases for fast-saas-quarkus.
# Drops and recreates each database and its owner role from scratch.
# Requires passwordless sudo access to the postgres OS user.
set -euo pipefail

psql_cmd() {
    local db="$1"; shift
    sudo -n -u postgres psql -d "$db" -v ON_ERROR_STOP=1 -c "$@"
}

recreate() {
    local db="$1"
    local user="$2"
    local pass="$3"

    echo "  dropping  : database '$db'"
    psql_cmd postgres "DROP DATABASE IF EXISTS \"$db\" WITH (FORCE)"
    echo "  dropping  : role '$user'"
    psql_cmd postgres "DROP USER IF EXISTS \"$user\""
    echo "  creating  : role '$user'"
    psql_cmd postgres "CREATE USER \"$user\" WITH PASSWORD '$pass'"
    echo "  creating  : database '$db' (owner $user)"
    psql_cmd postgres "CREATE DATABASE \"$db\" OWNER \"$user\""
    psql_cmd postgres "GRANT ALL PRIVILEGES ON DATABASE \"$db\" TO \"$user\""
    echo "  done      : $user @ $db"
}

echo
echo "=== dispatch-service ==="
recreate "dispatch" "dispatch" "dispatch"

echo
echo "=== cluster shard-1 ==="
recreate "cluster" "cluster" "cluster"

echo
echo "All databases ready."
echo
echo "To run Flyway migrations for dispatch-service:"
echo "  DISPATCH_RUN_MIGRATIONS=true ./mvnw quarkus:run -pl dispatch-service"
