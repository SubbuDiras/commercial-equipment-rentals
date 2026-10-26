# Pilot Hardening and Operations Runbook

This runbook supplements the local development guide with production-oriented controls. Do not expose the backend management port, database, object store, or Keycloak administration console to the public internet.

## Production configuration

Run the backend with the `prod` Spring profile. Set the following secrets and settings through the deployment secret manager, not source control:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `OIDC_ISSUER_URI`
- `S3_ENDPOINT`, `S3_REGION`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_PRIVATE_BUCKET`
- `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`
- `PAYMENT_OPERATIONS_ENABLED` (defaults to `false`)
- `PAYMENT_PRODUCTION_APPROVED` (defaults to `false`)
- `PAYMENT_SANDBOX_ONLY` (defaults to `true`)
- `OTP_SECRET` (a randomly generated secret of at least 32 bytes)
- `CORS_ALLOWED_ORIGINS` (comma-separated exact HTTPS origins; no wildcard)

Use TLS for MySQL and S3-compatible storage endpoints, rotate credentials, and grant the application only the minimum required database and bucket permissions. The production profile requires its connection credentials instead of silently falling back to development defaults.

Keep all three payment controls at their defaults until provider, legal, tax, and accounting approvals are recorded. Production financial operations require all of `PAYMENT_OPERATIONS_ENABLED=true`, `PAYMENT_PRODUCTION_APPROVED=true`, and `PAYMENT_SANDBOX_ONLY=false`; an accidental enablement or production credential alone cannot activate them. The sandbox-only flag must remain true in non-production, where the service also rejects any key not prefixed `rzp_test_`. Never validate the production gate by initiating a live transaction.

The Nginx production configuration is `infrastructure/nginx/nginx.prod.conf`. Mount the deployed certificate and private key read-only at `/etc/nginx/tls/fullchain.pem` and `/etc/nginx/tls/privkey.pem`; replace `server_name _;` with the approved domain before deployment. Configure Keycloak with `/auth` as its public relative path so the forwarded path and OIDC issuer match `OIDC_ISSUER_URI`. The production Angular build uses same-origin `/api/v1` and `/auth` paths; local development continues to use the local Keycloak endpoint. Validate with `nginx -t` and verify that HTTP redirects to the configured HTTPS domain. The configuration applies per-IP API request limiting and baseline browser security headers. Rate limits must be tuned using observed pilot traffic.

The backend exposes Prometheus metrics at `/actuator/prometheus`, requires an administrator JWT, and uses management port 8082 in the production profile. Keep that port on a private monitoring network and restrict firewall access to the monitoring host. Nginx deliberately returns 404 for public actuator paths.

## Encrypted backup schedule

Install `infrastructure/backups/backup.sh` as `/opt/marketplace/backups/backup.sh` and set its mode to `0750`. Install the provided systemd unit and timer under `/etc/systemd/system/`, then create `/etc/marketplace/backup.env` readable only by the backup service account. Generate an Age key pair with `age-keygen`; give the backup job only the public recipient, and store the private identity separately in the secrets vault. Configure:

```text
DB_HOST=private-database-host
DB_PORT=3306
DB_NAME=marketplace
DB_USERNAME=backup-user
DB_PASSWORD=managed-secret
S3_ALIAS=backup-source
S3_BUCKETS="marketplace-media marketplace-private"
BACKUP_DIR=/srv/marketplace-backups
BACKUP_DESTINATION=offsite-vault/marketplace
BACKUP_AGE_RECIPIENT=age1-public-recipient
```

Configure the `mc` source alias with read-only access to every bucket in `S3_BUCKETS` and the destination alias with write-only access before enabling the timer. The backup user requires database dump privileges and read access to the source buckets. Restrict the backup directory and destination bucket; apply off-host retention and immutability policies. Protect the Age private identity in the organization's secrets vault; losing it makes backups unrecoverable.

Enable and check the timer:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now marketplace-backup.timer
systemctl list-timers marketplace-backup.timer
sudo systemctl start marketplace-backup.service
sudo journalctl -u marketplace-backup.service
```

The script exports MySQL in a consistent transaction, mirrors every bucket listed in `S3_BUCKETS`, encrypts the archive using Age authenticated encryption, and copies the encrypted artifact to the off-host destination. It does not delete old backups; apply retention to the off-host encrypted copies only.

## Restore drill

Run restore drills against a clean, isolated MySQL database and empty object buckets. Install `infrastructure/backups/restore.sh`, configure the target `mc` alias, and provide the database, `S3_BUCKETS`, `AGE_IDENTITY_FILE`, `BACKUP_FILE`, and `RESTORE_CONFIRM=YES`. The script refuses non-empty database or object bucket targets. It does not overwrite existing objects or database tables.

After restore:

1. Confirm Flyway reports all expected migrations and compare critical row counts with expected pilot data.
2. Confirm object counts and sample object hashes against the source inventory.
3. Start the application against the restored services and run health checks and a renter-to-owner booking/payment/return smoke test using provider sandbox credentials.
4. Record elapsed time, result, backup identifier, database/object counts, and any corrective actions.

Run the drill quarterly and after changes to database or object-storage configuration. The 15-minute target is not considered met until measured on representative pilot data.

## Pilot launch checklist

- Verify CI succeeds, including backend tests, OWASP dependency analysis (CVSS threshold 7.0), the full frontend dependency audit, frontend unit tests, and Angular build.
- Run MySQL 8.4 migration and concurrency tests with Docker available.
- Confirm TLS, exact CORS origins, webhook signature configuration, admin access, alerting, and restricted management/database/storage ports.
- Perform and document a restore drill before accepting production transactions.
- Validate owner, renter, support, and escalation contacts; payment reconciliation and dispute procedures; backup failure alerts; and rollback steps.
- Run the full rental lifecycle against the Razorpay sandbox and private storage before pilot transactions. The repository's automated Phase 4 tests mock Razorpay and do not certify provider reconciliation or end-to-end production journeys.
- Provider failures or timeouts around irreversible refund/transfer calls require reconciliation against Razorpay's dashboard before retrying; database transactions cannot roll back a provider-side financial operation.

The frontend build and test toolchain has been migrated from Karma to Angular's Vitest unit-test builder, and the Angular application/dev-server builders use `@angular/build`. The current full `npm audit --audit-level=high` passes with zero vulnerabilities. Keep this full audit in CI so development-toolchain advisories are not hidden by a production-only scan.

Configure the `NVD_API_KEY` GitHub Actions secret to avoid slow NVD rate-limited downloads during OWASP dependency analysis.
