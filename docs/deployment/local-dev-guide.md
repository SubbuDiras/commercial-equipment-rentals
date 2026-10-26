# Local Development Environment & Setup Guide
## Commercial Equipment Rental Marketplace (Phase 1 Foundation)

This guide provides step-by-step instructions for running the complete development environment on a local workstation.

---

## 1. Prerequisites

Ensure the following tools are installed on your machine:
- **Docker & Docker Compose** (v24+)
- **Java Development Kit (JDK 21 LTS)** (e.g., Eclipse Temurin 21)
- **Node.js (v22.22.3+ or v24.x)** & **npm (v10+)**
- **Chrome/Chromium** (required for the Karma headless-browser tests)
- **Git**

---

## 2. Infrastructure Stack (Docker Compose)

The local infrastructure services run via `docker-compose.yml` located in `/infrastructure`:
- **MySQL 8.4:** Host port `3306` by default (container port `3306`)
- **Keycloak 25.0 (Quarkus):** Port `8080` (Realm: `marketplace-realm`)
- **MinIO (S3-compatible Object Storage):** Port `9000` (API) / `9001` (Console). Compose uses a pinned archived Bitnami image for local development only; do not use it in production.
- **Private inspection bucket:** `marketplace-private` (created without public-read access)
- **Private KYC and equipment media:** KYC documents remain private; public listings expose only time-limited signed image reads. Compose configures MinIO browser CORS for `http://localhost:4200`; configure the equivalent narrow origin allowlist on any other S3-compatible provider.
- **Mailpit / Mailhog (Local SMTP & Web UI):** Port `1025` (SMTP) / `8025` (Web UI)

### Starting Local Services:
```bash
# Navigate to infrastructure directory
cd infrastructure

# Start all dependent services in background
docker compose up -d

# Verify all containers are healthy
docker compose ps
```

If another local MySQL service already occupies port `3306`, set `$env:MYSQL_HOST_PORT = '3307'` before starting Compose. Set the same variable in the PowerShell session used to start the backend so its JDBC URL matches. The default remains `3306` for an unmodified local setup.

---

## 3. Keycloak Local Realm Configuration

Keycloak comes pre-configured with the `marketplace-realm` imported automatically:
- **Admin Console:** `http://localhost:8080/admin` (User: `admin`, Password: `admin_password`)
- **Realm:** `marketplace-realm`
- **Public Client (Angular SPA):** `marketplace-frontend` (Redirect URI: `http://localhost:4200/*`)
- **Resource Server Client:** `marketplace-backend`
- **Pre-seeded Test Users:**
  - `renter@example.com` / `Password123!` (Roles: `ROLE_RENTER`)
  - `owner@example.com` / `Password123!` (Roles: `ROLE_OWNER`, `ROLE_RENTER`)
  - `admin@example.com` / `Password123!` (Roles: `ROLE_ADMIN`)

---

## 4. Backend Setup (Spring Boot 3.3 + Gradle)

### 4.1 Local Environment Configuration (`application-dev.yml`)
The `dev` profile has local-only defaults matching Docker Compose, so a clean checkout does not need a `.env` file or exported variables to connect to the local services:
```yaml
spring:
  application:
    name: marketplace-backend
  datasource:
    url: jdbc:mysql://localhost:3306/marketplace_dev?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: marketplace_user
    password: marketplace_secret
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/marketplace-realm
          jwk-set-uri: http://localhost:8080/realms/marketplace-realm/protocol/openid-connect/certs

storage:
  s3:
    endpoint: http://localhost:9000
    access-key: minioadmin
    secret-key: minioadminpassword
    bucket-name: marketplace-private
    region: ap-south-1

payment:
  operations:
    enabled: false
    sandbox-only: true
  razorpay:
    key-id: ""
    key-secret: ""
    webhook-secret: ""
    api-base-url: http://localhost:18080/v1
```

These are example resolved local settings; `application-dev.yml` supplies the defaults. If using host port `3307`, set `$env:MYSQL_HOST_PORT = '3307'` before both `docker compose up -d` and the backend command; the JDBC default incorporates that variable. Override other values only when your local service configuration differs. Payment/deposit/refund/settlement operations are disabled by default; Compose does not provide a payment gateway mock. Automated Phase 4 lifecycle tests mock the provider. Do not add production credentials to a local profile or commit secrets. The OTP secret default is for local development only and must never be used in production.

### 4.2 Building and Running the Backend:
```bash
cd backend

# Run unit tests and MySQL migration integration tests (Docker is required for Testcontainers)
./gradlew test

# Start the application server (runs on port 8081 to avoid conflict with Keycloak on 8080)
./gradlew bootRun
```

On Windows, use `gradlew.bat`. In IntelliJ IDEA, open the `backend` folder or `backend/settings.gradle.kts` as a Gradle project, set the Gradle JVM to Java 21, and reload the Gradle project. Start Docker Compose first so MySQL, Keycloak, and MinIO are available.

On Windows, run:
```powershell
cd D:\Rentals\backend
# $env:MYSQL_HOST_PORT = '3307'
.\gradlew.bat test
.\gradlew.bat bootRun
```

In IntelliJ, use the Gradle tool window to run `test` or `bootRun`; do not use the old Maven project or the stale `MarketplaceApplication` run configuration. The Java toolchain compiles and runs the application with Java 21. Use a Java 21 JDK for Gradle as well.

If MySQL reports “Access denied” even though the Compose credentials look correct, its `mysql_data` volume may have been initialized earlier with a different password. Set `DB_USERNAME` and `DB_PASSWORD` in the IntelliJ Gradle run configuration to the credentials already used by that database. Do not delete the volume unless you have confirmed its data is disposable or backed up.

The MySQL integration tests start an empty MySQL 8.4 Testcontainers database, verify all eight Flyway migrations, and check that exactly one of ten simultaneous overlapping booking requests succeeds. Docker must be running for these tests; if Docker is unavailable, `@Testcontainers(disabledWithoutDocker = true)` skips them, so a green Gradle build with skipped tests is not database acceptance.

### 4.3 Verifying Backend Health:
- **Health Check:** `http://localhost:8081/actuator/health`
- **OpenAPI Swagger UI:** `http://localhost:8081/swagger-ui.html`

---

## 5. Frontend Setup (Angular 22)

### 5.1 Installing Dependencies and Running Dev Server:
```bash
cd frontend

# Install dependencies
npm install

# Start development server with proxy to backend
npm start
```
The Angular application will be available at `http://localhost:4200`.

Run frontend unit tests in headless Chrome with:
```powershell
npm test
```

---

## Production and Pilot Operations

Production profile requirements, Nginx TLS setup, CI vulnerability scans, encrypted backup scheduling, and restore-drill instructions are documented in [the pilot hardening runbook](./pilot-hardening-runbook.md). Do not use the local-development credentials or HTTP endpoints in a production deployment.

## 6. Local Verification

Run the automated checks from the project root:

```powershell
cd D:\Rentals\backend
.\gradlew.bat test

cd D:\Rentals\frontend
npm test
npm run build
```

MySQL/Testcontainers migration and concurrency tests require a running Docker engine. If Docker is unavailable, those tests are skipped, so database-specific acceptance remains unverified.

For a manual smoke test, start the Docker Compose services, run the backend with the `dev` profile and start the Angular development server. Verify health, sign-in against the imported Keycloak realm, and public catalogue loading. Seeded sample accounts are for local development only; never reuse them outside the local environment.

The repository's integration coverage is not end-to-end production acceptance. Complete the regional launch checklist, provider reconciliation, security review, and legal/tax approvals before enabling real users or live financial flows.

To verify protected API access from PowerShell after Keycloak and the backend are ready:

```powershell
$tokenResponse = Invoke-RestMethod -Method Post `
  -Uri 'http://localhost:8080/realms/marketplace-realm/protocol/openid-connect/token' `
  -ContentType 'application/x-www-form-urlencoded' `
  -Body @{
    grant_type = 'password'
    client_id = 'marketplace-frontend'
    username = 'renter@example.com'
    password = 'Password123!'
    scope = 'openid'
  }

Invoke-RestMethod -Uri 'http://localhost:8081/api/v1/test/renter-only' `
  -Headers @{ Authorization = "Bearer $($tokenResponse.access_token)" }
```

The local Keycloak realm enables the password grant only to support this smoke test. The frontend itself uses Authorization Code with PKCE. Do not enable password grants for a production client. In the browser, open `http://localhost:4200`, sign in using the local renter account, and confirm the catalogue page loads.
