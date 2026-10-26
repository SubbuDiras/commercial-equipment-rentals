# Technology Stack Specification & Version Matrix
## Commercial Equipment Rental Marketplace (Phase 0)

This is the **repository's implementation baseline**, not a claim that every version is currently supported or the newest available. Reassess support/security lifecycle before production deployment; upgrading framework versions must be handled as a separate compatibility change.

---

## 1. Version Compatibility Matrix

| Component | Selected Version | Support Lifecycle | Compatibility Rationale |
|---|---|---|---|
| **Java JDK** | **Java 21 LTS** (Eclipse Temurin / OpenJDK) | Active LTS through Sept 2028 | Latest LTS with Virtual Threads (Project Loom), modern language features, and full Spring Boot 3.3+ optimization. |
| **Spring Boot** | **3.3.4** | Repository baseline | Version pinned in `backend/build.gradle.kts`; support lifecycle must be checked before deployment. |
| **Gradle** | **Gradle 8.10.2** | Repository baseline | Version pinned by the Gradle wrapper. |
| **Database** | **MySQL 8.4** | Repository baseline | InnoDB; local and integration-test database version should match the deployed target. |
| **Database Migration**| Spring Boot-managed Flyway with `flyway-core` and `flyway-mysql` | Repository baseline | Version is dependency-managed; no independent Flyway 10.x pin is declared. |
| **MySQL Integration Tests** | **Testcontainers 1.21.4** | Repository test baseline | The version is imported through `dependencyManagement` to override Spring Boot's older managed version; MySQL 8.4 migration and concurrency tests run when Docker is available. |
| **Identity Provider** | **Keycloak 25.0.x** (Quarkus runtime) | Repository baseline | Containerized OIDC provider; verify support lifecycle and configuration before deployment. |
| **Frontend Framework**| **Angular 22.2.x** | Repository baseline | Angular packages are declared at `^22.2.3` in `frontend/package.json`; verify vendor support lifecycle before deployment. |
| **TypeScript** | **TypeScript 6.0.x** | Repository baseline | Declared at `~6.0.3`; strict compiler options are defined in the frontend tsconfig files. |
| **API Documentation** | **SpringDoc OpenAPI 2.6.0** | Aligned with Spring Boot 3.3 | Generates OpenAPI 3.0 spec and Swagger UI natively from Spring Web annotations. |
| **Payment Gateway** | Razorpay REST adapter (custom `RestClient`) | Evaluation/development integration | No Razorpay Java SDK dependency is declared. Provider eligibility and funds flow are unapproved; no live processing is authorized by this design. |

---

## 2. Spring Boot Gradle Dependencies (`backend/build.gradle.kts`)

```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.rental"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.21.4")
    }
}

dependencies {
    // Web, validation, health checks, and persistence
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly("com.mysql:mysql-connector-j")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
    testRuntimeOnly("com.h2database:h2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

---

## 3. Angular Configuration (repository excerpt)

```json
{
  "name": "equipment-rental-frontend",
  "version": "0.1.0",
  "private": true,
  "scripts": {
    "ng": "ng",
    "start": "ng serve",
    "build": "ng build",
    "watch": "ng build --watch --configuration development",
    "test": "ng test --watch=false"
  },
  "dependencies": {
    "@angular/animations": "^22.2.3",
    "@angular/common": "^22.2.3",
    "@angular/compiler": "^22.2.3",
    "@angular/core": "^22.2.3",
    "@angular/forms": "^22.2.3",
    "@angular/platform-browser": "^22.2.3",
    "@angular/platform-browser-dynamic": "^22.2.3",
    "@angular/router": "^22.2.3",
    "angular-oauth2-oidc": "^17.0.2",
    "rxjs": "~7.8.0",
    "tslib": "^2.3.0",
    "zone.js": "~0.16.0"
  },
  "devDependencies": {
    "@angular-devkit/build-angular": "^22.2.3",
    "@angular/cli": "^22.2.3",
    "@angular/compiler-cli": "^22.2.3",
    "@types/jasmine": "~5.1.0",
    "jasmine-core": "~5.1.0",
    "karma": "~6.4.0",
    "karma-chrome-launcher": "~3.2.0",
    "karma-coverage": "~2.2.0",
    "karma-jasmine": "~5.1.0",
    "karma-jasmine-html-reporter": "~2.1.0",
    "typescript": "~6.0.3"
  }
}
```
