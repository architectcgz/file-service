#!/bin/sh
set -eu

DB_HOST="${DB_HOST:-postgres}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-file_service}"
DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres123456}"
MIGRATIONS_DIR="${MIGRATIONS_DIR:-/migrations/sql}"
MIGRATION_TABLE="${MIGRATION_TABLE:-schema_migrations}"
DEFAULT_BUCKET="${S3_BUCKET:-platform-files}"

export PGPASSWORD="${DB_PASSWORD}"

wait_for_db() {
  echo "Waiting for PostgreSQL at ${DB_HOST}:${DB_PORT}/${DB_NAME}..."
  until pg_isready -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" -d "${DB_NAME}" >/dev/null 2>&1; do
    sleep 2
  done
}

create_migration_table() {
  psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" -d "${DB_NAME}" -v ON_ERROR_STOP=1 <<SQL
CREATE TABLE IF NOT EXISTS ${MIGRATION_TABLE} (
    version VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
SQL
}

apply_migration() {
  file="$1"
  filename="$(basename "${file}")"
  version="${filename%%__*}"
  name="${filename#*__}"
  name="${name%.sql}"
  checksum="$(sha256sum "${file}" | awk '{print $1}')"

  applied_checksum="$(
    psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" -d "${DB_NAME}" -tAc \
      "SELECT checksum FROM ${MIGRATION_TABLE} WHERE version = '${version}'"
  )"
  applied_checksum="$(echo "${applied_checksum}" | tr -d '[:space:]')"

  if [ -n "${applied_checksum}" ]; then
    if [ "${applied_checksum}" != "${checksum}" ]; then
      echo "Migration checksum mismatch: ${filename}"
      exit 1
    fi
    echo "Skipping already applied migration: ${filename}"
    return 0
  fi

  echo "Applying migration: ${filename}"
  temp_sql="$(mktemp)"
  cat > "${temp_sql}" <<SQL
BEGIN;
\i ${file}
INSERT INTO ${MIGRATION_TABLE} (version, name, checksum)
VALUES ('${version}', '${name}', '${checksum}');
COMMIT;
SQL

  psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" -d "${DB_NAME}" \
    -v ON_ERROR_STOP=1 -v default_bucket="${DEFAULT_BUCKET}" -f "${temp_sql}"
  rm -f "${temp_sql}"
}

main() {
  wait_for_db
  create_migration_table

  found=0
  for file in "${MIGRATIONS_DIR}"/*.sql; do
    if [ ! -f "${file}" ]; then
      continue
    fi
    found=1
    apply_migration "${file}"
  done

  if [ "${found}" -eq 0 ]; then
    echo "No migration scripts found in ${MIGRATIONS_DIR}"
  else
    echo "All migrations applied successfully."
  fi
}

main "$@"
