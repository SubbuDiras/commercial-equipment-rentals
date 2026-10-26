#!/usr/bin/env bash
set -euo pipefail

required=(DB_HOST DB_PORT DB_NAME DB_USERNAME DB_PASSWORD S3_ALIAS S3_BUCKETS BACKUP_DIR BACKUP_DESTINATION BACKUP_AGE_RECIPIENT)
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    printf 'Required environment variable is missing: %s\n' "$name" >&2
    exit 2
  fi
done

command -v mysqldump >/dev/null
command -v mc >/dev/null
command -v age >/dev/null
command -v tar >/dev/null
mkdir -p "$BACKUP_DIR"

stage="$(mktemp -d)"
trap 'rm -rf -- "$stage"' EXIT
backup_name="marketplace-$(date -u +%Y%m%dT%H%M%SZ).tar.gz.age"
read -r -a buckets <<< "$S3_BUCKETS"
if (( ${#buckets[@]} == 0 )); then
  printf 'At least one S3 bucket must be configured in S3_BUCKETS.\n' >&2
  exit 2
fi

MYSQL_PWD="$DB_PASSWORD" mysqldump \
  --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" \
  --single-transaction --routines --triggers --events --hex-blob "$DB_NAME" \
  > "$stage/database.sql"
mkdir -p "$stage/objects"
for bucket in "${buckets[@]}"; do
  mc mirror --overwrite "$S3_ALIAS/$bucket" "$stage/objects/$bucket"
done

tar -czf - -C "$stage" . |
  age --encrypt --recipient "$BACKUP_AGE_RECIPIENT" --output "$BACKUP_DIR/$backup_name"
if [[ ! -s "$BACKUP_DIR/$backup_name" ]]; then
  printf 'Encrypted backup was not created.\n' >&2
  exit 1
fi
mc cp "$BACKUP_DIR/$backup_name" "$BACKUP_DESTINATION/$backup_name"

printf 'Encrypted backup created and copied off-host: %s\n' "$BACKUP_DIR/$backup_name"
