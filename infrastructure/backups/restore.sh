#!/usr/bin/env bash
set -euo pipefail

required=(DB_HOST DB_PORT DB_NAME DB_USERNAME DB_PASSWORD S3_ALIAS S3_BUCKETS AGE_IDENTITY_FILE BACKUP_FILE RESTORE_CONFIRM)
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    printf 'Required environment variable is missing: %s\n' "$name" >&2
    exit 2
  fi
done
if [[ "$RESTORE_CONFIRM" != "YES" ]]; then
  printf 'Set RESTORE_CONFIRM=YES only after verifying the clean restore targets.\n' >&2
  exit 2
fi
if [[ ! -f "$BACKUP_FILE" ]]; then
  printf 'Backup file does not exist: %s\n' "$BACKUP_FILE" >&2
  exit 2
fi

command -v mysql >/dev/null
command -v mc >/dev/null
command -v age >/dev/null
stage="$(mktemp -d)"
trap 'rm -rf -- "$stage"' EXIT
read -r -a buckets <<< "$S3_BUCKETS"
if (( ${#buckets[@]} == 0 )); then
  printf 'At least one S3 bucket must be configured in S3_BUCKETS.\n' >&2
  exit 2
fi

age --decrypt --identity "$AGE_IDENTITY_FILE" "$BACKUP_FILE" |
  tar -xzf - -C "$stage"

tables="$(MYSQL_PWD="$DB_PASSWORD" mysql \
  --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --database="$DB_NAME" \
  --batch --skip-column-names --execute="SHOW TABLES")"
if [[ -n "$tables" ]]; then
  printf 'Refusing to restore into a database that already contains tables.\n' >&2
  exit 2
fi
for bucket in "${buckets[@]}"; do
  objects="$(mc ls "$S3_ALIAS/$bucket")"
  if [[ -n "$objects" ]]; then
    printf 'Refusing to restore into non-empty object bucket: %s\n' "$bucket" >&2
    exit 2
  fi
  if [[ ! -d "$stage/objects/$bucket" ]]; then
    printf 'Backup does not contain expected object bucket: %s\n' "$bucket" >&2
    exit 2
  fi
done

MYSQL_PWD="$DB_PASSWORD" mysql \
  --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --database="$DB_NAME" \
  < "$stage/database.sql"
for bucket in "${buckets[@]}"; do
  mc mirror "$stage/objects/$bucket" "$S3_ALIAS/$bucket"
done

printf 'Restore completed into the verified empty database and bucket.\n'
