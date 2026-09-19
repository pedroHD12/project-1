# Hosted MailFlow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make MailFlow deployable as a secure, owner-only website that reliably completes scheduled email jobs after downtime.

**Architecture:** Keep the Spring Boot + Thymeleaf application as the single web and worker process. Add explicit `local` and `cloud` runtime profiles, use a cloud-only AES-GCM credential protector, and change delivery claiming so overdue jobs are sent once and identified as late. Build a Docker image and deployment runbook for an Oracle VM behind an HTTPS reverse proxy and a Supabase PostgreSQL database.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring Security, Spring JDBC/JPA, Flyway, PostgreSQL, Thymeleaf, Docker, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-18-hosted-mailflow-design.md`

## Global Constraints

- Preserve the local profile and Windows DPAPI behavior; cloud must never silently fall back to DPAPI.
- Cloud deployment requires HTTPS and a PostgreSQL TLS connection with certificate verification.
- No password, token, address list, body content or encrypted credential may enter logs, errors, Git, Docker layers, or test fixtures.
- Cloud has one owner only; public registration is disabled after setup and no multi-user invitation feature is added.
- Delivery jobs are at-most-once after uncertain SMTP acceptance; overdue pending jobs become `SENT_LATE` only after a confirmed SMTP acceptance.
- Keep the existing PostgreSQL/Flyway schema as the source of truth and make every schema change forward-only.

## Review Focus

- A cloud deployment with a non-TLS `DB_URL` must refuse to start rather than exposing database traffic.
- A guessed, expired, or reused owner setup token must not create an account.
- A modified AES-GCM credential value must fail without revealing plaintext or cipher internals.
- A job more than 15 minutes late must send once after recovery and report `SENT_LATE`, not disappear as `MISSED`.
- A local installation with `dpapi:` SMTP data must continue to run unchanged.

---

### Task 1: Add explicit local and cloud runtime boundaries

**Files:**
- Create: `src/main/java/br/com/mailflow/config/AppRuntimeProperties.java`
- Create: `src/main/java/br/com/mailflow/config/CloudStartupValidator.java`
- Create: `src/main/resources/application-cloud.yml`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/br/com/mailflow/security/LocalAccessFilter.java`
- Modify: `src/main/java/br/com/mailflow/config/SecurityConfig.java`
- Test: `src/test/java/br/com/mailflow/config/CloudStartupValidatorTest.java`
- Test: `src/test/java/br/com/mailflow/security/LocalAccessFilterTest.java`

**Interfaces:**
- Consumes: Spring `Environment`, `app.runtime` properties and the existing `LocalAccessFilter`.
- Produces: `AppRuntimeProperties.isCloud(): boolean`; startup validation callable before the application accepts requests.

- [ ] **Step 1: Write failing cloud profile tests**

```java
@Test
void cloudRejectsDatabaseUrlWithoutVerifyFull() {
    var properties = new AppRuntimeProperties("cloud", "jdbc:postgresql://db.example/mailflow");
    assertThatThrownBy(() -> CloudStartupValidator.validate(properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("verificação TLS");
}

@Test
void localFilterDoesNotBlockCloudRequests() throws Exception {
    var filter = new LocalAccessFilter(new AppRuntimeProperties("cloud", "jdbc:postgresql://x?sslmode=verify-full"));
    // MockMvc request has remote address 203.0.113.10 and host app.example.com.
    assertThat(filterAllowsRequest(filter)).isTrue();
}
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `./mvnw -Dtest=CloudStartupValidatorTest,LocalAccessFilterTest test`

Expected: compilation failure because the properties and validator do not exist.

- [ ] **Step 3: Implement the minimal profile boundary**

```java
@ConfigurationProperties(prefix = "app.runtime")
public record AppRuntimeProperties(String mode, String databaseUrl) {
    public boolean isCloud() { return "cloud".equals(mode); }
}

static void validate(AppRuntimeProperties runtime) {
    if (runtime.isCloud() && !runtime.databaseUrl().contains("sslmode=verify-full")) {
        throw new IllegalStateException("Cloud exige PostgreSQL com verificação TLS.");
    }
}
```

Register configuration properties. Make `LocalAccessFilter` immediately call `chain.doFilter` in cloud mode, retain the exact localhost checks in local mode, and change `SecurityConfig` to select secure cookies and HSTS only in cloud mode.

Add `application-cloud.yml` with `server.forward-headers-strategy=native`, template caching, secure cookies, and `server.address=127.0.0.1`. Keep the public listener bound locally because Caddy/Nginx is the only public entry point.

- [ ] **Step 4: Run focused tests and the existing security suite**

Run: `./mvnw -Dtest=CloudStartupValidatorTest,LocalAccessFilterTest,PublicErrorTest,AuthenticationBudgetTest test`

Expected: PASS. Confirm that the old local-only filter tests still pass.

- [ ] **Step 5: Commit the runtime boundary**

```bash
git add src/main/java/br/com/mailflow/config src/main/java/br/com/mailflow/security/LocalAccessFilter.java src/main/resources/application*.yml src/test/java/br/com/mailflow/config src/test/java/br/com/mailflow/security/LocalAccessFilterTest.java
git commit -m "feat: add secure cloud runtime profile"
```

### Task 2: Restrict cloud access to one bootstrap owner

**Files:**
- Create: `src/main/java/br/com/mailflow/security/OwnerSetupProperties.java`
- Create: `src/main/java/br/com/mailflow/security/OwnerSetupController.java`
- Create: `src/main/resources/templates/auth/setup.html`
- Modify: `src/main/java/br/com/mailflow/security/AccountStore.java`
- Modify: `src/main/java/br/com/mailflow/security/RegistrationController.java`
- Modify: `src/main/java/br/com/mailflow/config/SecurityConfig.java`
- Test: `src/test/java/br/com/mailflow/security/OwnerSetupControllerTest.java`
- Test: `src/test/java/br/com/mailflow/security/AccountStoreTest.java`

**Interfaces:**
- Consumes: `OWNER_EMAIL`, `INITIAL_OWNER_SETUP_TOKEN`, `AppRuntimeProperties`, `AccountStore.register` semantics.
- Produces: `AccountStore.createInitialOwner(RegistrationForm form, char[] token): boolean` and public GET/POST `/setup` valid only before an owner exists.

- [ ] **Step 1: Write owner bootstrap failures first**

```java
@Test
void cloudRejectsSetupWhenEmailDoesNotMatchOwner() {
    assertThat(store.createInitialOwner(form("other@example.com"), token("expected"))).isFalse();
}

@Test
void setupCannotBeUsedTwice() {
    assertThat(store.createInitialOwner(form("owner@example.com"), token("expected"))).isTrue();
    assertThat(store.createInitialOwner(form("owner@example.com"), token("expected"))).isFalse();
}

@Test
void registerIsNotPublicInCloud() throws Exception {
    mockMvc.perform(get("/register").with(cloudProfile()))
        .andExpect(status().isNotFound());
}
```

- [ ] **Step 2: Run the focused bootstrap tests and verify they fail**

Run: `./mvnw -Dtest=OwnerSetupControllerTest,AccountStoreTest test`

Expected: FAIL because `/setup` and `createInitialOwner` do not exist.

- [ ] **Step 3: Implement one-time owner setup**

Implement `OwnerSetupProperties` with non-null cloud-only values. Compare the supplied token with `MessageDigest.isEqual` over UTF-8 bytes; reject missing or overlong tokens before comparison. `createInitialOwner` must lock `INITIAL_WORKSPACE`, require zero users, require normalized email equality, validate the `RegistrationForm`, write a BCrypt password hash, and return `false` for every denial without logging secret material.

Permit `/setup` in Spring Security only in cloud mode. Make `/register` return a 404 view in cloud mode and preserve its current first-user behavior in local mode. `/setup` must also return 404 once an owner exists.

- [ ] **Step 4: Run focused tests and registration regression tests**

Run: `./mvnw -Dtest=OwnerSetupControllerTest,AccountStoreTest,RegistrationSecurityTest,WorkspaceIsolationTest test`

Expected: PASS. Verify a second setup request does not insert a second `app_users` row.

- [ ] **Step 5: Commit owner-only access**

```bash
git add src/main/java/br/com/mailflow/security src/main/resources/templates/auth src/main/java/br/com/mailflow/config/SecurityConfig.java src/test/java/br/com/mailflow/security
git commit -m "feat: restrict cloud setup to one owner"
```

### Task 3: Add cloud credential encryption with authenticated AES-GCM

**Files:**
- Create: `src/main/java/br/com/mailflow/settings/smtp/AesGcmSecretProtector.java`
- Create: `src/main/java/br/com/mailflow/settings/smtp/SecretProtectorConfiguration.java`
- Create: `src/main/java/br/com/mailflow/settings/smtp/CredentialKeyRing.java`
- Modify: `src/main/java/br/com/mailflow/settings/smtp/WindowsDpapiSecretProtector.java`
- Modify: `src/main/java/br/com/mailflow/settings/smtp/SmtpAccountService.java`
- Test: `src/test/java/br/com/mailflow/settings/smtp/AesGcmSecretProtectorTest.java`
- Test: `src/test/java/br/com/mailflow/settings/smtp/SecretProtectorConfigurationTest.java`

**Interfaces:**
- Consumes: `MAILFLOW_CREDENTIAL_KEY_V1` (base64-encoded 32-byte secret) and optional `MAILFLOW_CREDENTIAL_KEY_V0`.
- Produces: secret strings encoded as `aesgcm:v1:<base64-nonce>:<base64-ciphertext>`; `SecretProtector.protect/unprotect` remains the application interface.

- [ ] **Step 1: Write failing encryption tests**

```java
@Test
void roundTripsWithoutEmbeddingPlaintext() {
    var protector = protectorWithKey("v1", keyBytes(1));
    var protectedValue = protector.protect("gmail-app-password");
    assertThat(protectedValue).startsWith("aesgcm:v1:").doesNotContain("gmail-app-password");
    assertThat(protector.unprotect(protectedValue)).isEqualTo("gmail-app-password");
}

@Test
void rejectsTamperedCiphertext() {
    var value = protectorWithKey("v1", keyBytes(1)).protect("secret");
    assertThatThrownBy(() -> protectorWithKey("v1", keyBytes(1)).unprotect(value + "x"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Credencial protegida inválida.");
}

@Test
void localConfigurationSelectsDpapiAndCloudSelectsAesGcm() { /* assert bean types */ }
```

- [ ] **Step 2: Run focused encryption tests and verify they fail**

Run: `./mvnw -Dtest=AesGcmSecretProtectorTest,SecretProtectorConfigurationTest test`

Expected: compilation failure because AES-GCM and profile selection do not exist.

- [ ] **Step 3: Implement authenticated encryption and strict configuration**

Use `Cipher.getInstance("AES/GCM/NoPadding")`, a fresh 12-byte `SecureRandom` nonce, a 128-bit GCM tag, and `Base64.getUrlEncoder().withoutPadding()`. Parse only four colon-separated fields. Map parse, missing-key, Base64 and authentication failures to the exact generic exception `IllegalStateException("Credencial protegida inválida.")`.

`CredentialKeyRing` must reject keys that are not exactly 32 bytes and choose the current `v1` key when protecting. It may decrypt `v0` only when the old key is configured. Configure DPAPI only under the local profile and AES-GCM only under cloud. Do not add either key to `application*.yml`, compose files, README examples, or logs.

In `SmtpAccountService.revealSecret`, after successfully decrypting a `v0` value in cloud, immediately replace it with a `v1` ciphertext in the same transaction. Do not attempt to migrate `dpapi:` values in cloud; show a safe configuration error requiring the user to enter the SMTP app password again.

- [ ] **Step 4: Run focused encryption and SMTP tests**

Run: `./mvnw -Dtest=AesGcmSecretProtectorTest,SecretProtectorConfigurationTest,SmtpAccountServiceTest,WindowsDpapiSecretProtectorTest test`

Expected: PASS. Confirm no assertion or log contains plaintext.

- [ ] **Step 5: Commit credential protection**

```bash
git add src/main/java/br/com/mailflow/settings/smtp src/test/java/br/com/mailflow/settings/smtp
git commit -m "feat: encrypt cloud SMTP credentials with aes-gcm"
```

### Task 4: Recover overdue deliveries without duplicates

**Files:**
- Create: `src/main/resources/db/migration/V6__late_delivery_recovery.sql`
- Modify: `src/main/java/br/com/mailflow/delivery/DispatchQueue.java`
- Modify: `src/main/java/br/com/mailflow/delivery/DispatchService.java`
- Modify: `src/main/java/br/com/mailflow/delivery/DispatchWorker.java`
- Test: `src/test/java/br/com/mailflow/delivery/DispatchQueueLateRecoveryTest.java`
- Test: `src/test/java/br/com/mailflow/migration/HostedDeliveryMigrationTest.java`

**Interfaces:**
- Consumes: existing `delivery_jobs` and `delivery_attempts`, existing `EmailGateway.Outcome` values.
- Produces: `DispatchQueue.Work.late(): boolean`; final job status `SENT_LATE`; a job that is late remains eligible for one safe claim.

- [ ] **Step 1: Write failing overdue-delivery tests**

```java
@Test
void claimsOverduePendingJobAsLateInsteadOfMarkingMissed() {
    var job = insertPendingJob(Instant.now().minus(Duration.ofMinutes(16)));
    var work = queue.claim();
    assertThat(work).hasValueSatisfying(value -> assertThat(value.late()).isTrue());
    assertThat(jobStatus(job)).isEqualTo("PROCESSING");
}

@Test
void acceptedLateJobEndsSentLateAndCannotBeClaimedAgain() {
    var work = claimOverdueJob();
    queue.finish(work, EmailGateway.Outcome.ACCEPTED, false);
    assertThat(jobStatus(work.id())).isEqualTo("SENT_LATE");
    assertThat(queue.claim()).isEmpty();
}
```

- [ ] **Step 2: Run focused queue tests and verify they fail**

Run: `./mvnw -Dtest=DispatchQueueLateRecoveryTest,HostedDeliveryMigrationTest test`

Expected: FAIL because overdue work becomes `MISSED` and the schema rejects `SENT_LATE`.

- [ ] **Step 3: Add the migration and minimal queue behavior**

`V6__late_delivery_recovery.sql` must replace the delivery-job status check constraint to include `SENT_LATE`; it must not change prior rows. Extend `Work` with `boolean late`. In `claim`, remove the branch that changes an overdue pending job to `MISSED`; carry the computed `missed` flag as `late` into the claim transaction. In `finish`, map `ACCEPTED` to `SENT_LATE` when `work.late()` is true, otherwise `SENT`. Preserve `UNKNOWN` behavior: never retry automatically after an ambiguous SMTP result.

Update `statusLabel`, list summaries and message completion query so `SENT_LATE` is successful completion and no longer marked as attention. Keep manual retry limited to `FAILED` and the existing explicitly released states.

- [ ] **Step 4: Run focused queue, migration and dispatch tests**

Run: `./mvnw -Dtest=DispatchQueueLateRecoveryTest,HostedDeliveryMigrationTest,DispatchIntegrationTest,WorkspaceFeaturesTest test`

Expected: PASS. Run the concurrency test twice to confirm only one worker sends the overdue job.

- [ ] **Step 5: Commit delayed-delivery recovery**

```bash
git add src/main/resources/db/migration/V6__late_delivery_recovery.sql src/main/java/br/com/mailflow/delivery src/test/java/br/com/mailflow/delivery src/test/java/br/com/mailflow/migration
git commit -m "feat: send overdue jobs once after recovery"
```

### Task 5: Containerize the cloud profile and document safe deployment

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`
- Create: `deploy/Caddyfile`
- Create: `deploy/docker-compose.cloud.yml`
- Create: `.github/workflows/verify.yml`
- Create: `.env.cloud.example`
- Modify: `README.md`
- Create: `docs/DEPLOY-ORACLE-SUPABASE.md`
- Test: `src/test/java/br/com/mailflow/config/CloudContainerConfigurationTest.java`

**Interfaces:**
- Consumes: packaged JAR, `cloud` profile environment variables and Docker `PORT` default 8080.
- Produces: a container that exposes only local port 8080 to Caddy, an HTTPS proxy configuration, and a documented manual first deployment.

- [ ] **Step 1: Write failing configuration tests**

```java
@Test
void cloudRequiresAllMandatorySecrets() {
    assertThatThrownBy(() -> startCloudContext(without("MAILFLOW_CREDENTIAL_KEY_V1")))
        .hasMessageContaining("MAILFLOW_CREDENTIAL_KEY_V1");
}

@Test
void cloudProfileDoesNotExposeActuatorBeyondHealth() {
    mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
}
```

- [ ] **Step 2: Run container configuration tests and verify they fail**

Run: `./mvnw -Dtest=CloudContainerConfigurationTest test`

Expected: FAIL because required cloud secret validation and test fixture do not exist.

- [ ] **Step 3: Create reproducible deployment assets**

Use a two-stage Dockerfile: Maven/JDK 25 builds with `./mvnw -B test package`; a JRE 25 runtime copies only the JAR, runs as an unprivileged `mailflow` user, and sets `SPRING_PROFILES_ACTIVE=cloud`. `.dockerignore` excludes `.git`, `target`, `.env*`, local databases and IDE files.

`deploy/docker-compose.cloud.yml` passes variables by name rather than literals, binds the application only to `127.0.0.1:8080`, and starts Caddy as the only public service. `Caddyfile` redirects HTTP to HTTPS, proxies to the app and adds HSTS. Do not put a domain, key, e-mail, database URL or SMTP credential in a committed file.

The GitHub workflow runs `./mvnw -B test package`, then `docker build --pull -t mailflow:${{ github.sha }} .`; it does not deploy or access external secrets. The deployment guide contains the exact user-performed Oracle, Supabase, firewall, domain, secret, backup and restore steps.

- [ ] **Step 4: Run focused configuration tests and image build**

Run: `./mvnw -Dtest=CloudContainerConfigurationTest test && docker build -t mailflow:cloud-check .`

Expected: PASS and a successful image build with no secret copied into image layers.

- [ ] **Step 5: Commit cloud deploy assets**

```bash
git add Dockerfile .dockerignore .env.cloud.example deploy .github/workflows/verify.yml README.md docs/DEPLOY-ORACLE-SUPABASE.md src/test/java/br/com/mailflow/config/CloudContainerConfigurationTest.java
git commit -m "feat: add hosted deployment assets"
```

### Task 6: Run release-grade verification and create the operator checklist

**Files:**
- Create: `docs/HOSTED-RELEASE-CHECKLIST.md`
- Modify: `README.md`
- Test: all existing test suites

**Interfaces:**
- Consumes: completed cloud profile, owner setup, encryption, late-delivery recovery and container assets.
- Produces: a reproducible pre-deploy checklist and evidence that the local profile remains usable.

- [ ] **Step 1: Add the operator checklist**

Include exact checks: database TLS `verify-full`; generated 32-byte credential key; owner setup token deleted from the host after successful setup; HTTPS certificate valid; `/register` and second `/setup` return 404; SMTP test uses a disposable recipient; backup exists and restore was tested; health endpoint works; an intentionally overdue test job finishes `SENT_LATE`; no credentials appear in container logs.

- [ ] **Step 2: Run local, cloud and migration tests**

Run: `./mvnw test`

Expected: PASS with the local DPAPI tests, cloud AES-GCM tests, owner setup tests, migration tests and queue recovery tests all included.

- [ ] **Step 3: Run static secret and configuration checks**

Run: `rg -n "DB_PASSWORD=.+|MAILFLOW_CREDENTIAL_KEY.+|INITIAL_OWNER_SETUP_TOKEN.+" --glob '!*.example' --glob '!docs/superpowers/**' .`

Expected: no committed secret values; variable references and documentation labels are allowed only in `.example` and documentation files.

- [ ] **Step 4: Build the release artifact**

Run: `./mvnw -B package && docker build -t mailflow:release-check .`

Expected: PASS. Record the artifact digest in the deployment notes, not in source code.

- [ ] **Step 5: Commit release documentation**

```bash
git add docs/HOSTED-RELEASE-CHECKLIST.md README.md
git commit -m "docs: add hosted release checklist"
```

## Execution order

Execute Tasks 1 through 6 in order. Tasks 1–4 change application behavior and each needs a green focused test cycle before the next task. Task 5 only starts after cloud configuration tests can prove startup validation. Task 6 is the release gate; actual deployment is intentionally manual because it needs the owner’s Oracle, Supabase, GitHub and domain access.
