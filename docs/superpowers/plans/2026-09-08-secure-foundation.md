# Secure foundation implementation plan

> **For agentic workers:** Use superpowers:executing-plans task by task. Review security-sensitive changes independently.

**Goal:** Deliver persistent local owner registration, login, workspace isolation and usable Portuguese navigation.

**Architecture:** Keep Spring MVC/Thymeleaf. Authenticate persisted accounts through JDBC transactions; all domain services derive the workspace from a trusted principal. Flyway backfills legacy rows and enforces workspace foreign keys.

**Tech Stack:** Java 25, Spring Boot 4.1.1, PostgreSQL, Flyway, JPA, MockMvc, JUnit.

**Spec:** ../specs/2026-09-04-secure-mailflow-evolution-design.md

## Global constraints

- Preserve legacy records; run verification against disposable databases before touching live data.
- Bind the app to 127.0.0.1. No real email during automated tests.
- BCrypt cost 12. Reject passwords above 72 UTF-8 bytes (correction to the spec's incompatible 128-character limit; BCrypt must not silently truncate passwords).
- One owner claims the reserved workspace transactionally. Registration closes after that.
- No credentials in logs or HTML. Presets and workspace checks are server-side.
- Existing dark theme, Portuguese copy, accessible focus and mobile menu.
- No new approval is needed for already-approved implementation; runtime deployment or access to real secrets is a separate operation.

## Task 1: Establish a recoverable and executable baseline

- [x] Archive source/config/docs (without real credentials) in ignored backups directory.
- [x] Create codex/secure-local-foundation branch.
- [x] Run existing tests using local Maven and record the result.

## Task 2: Registration, authentication and tenant context

Files: config/SecurityConfig.java; new security/{AccountAuthenticationProvider,AccountStore,AccountPrincipal,CurrentWorkspace,RegistrationForm,RegistrationController}.java; templates/auth/{login,register}.html; test security/RegistrationSecurityTest.java.

Interfaces: `AccountStore.register(RegistrationForm)`, `AccountStore.isRegistrationOpen()`, `CurrentWorkspace.id(): UUID`; principal exposes userId/workspaceId.

- [x] Write failing MockMvc tests: GET /login returns a Portuguese form; POST /register with CSRF creates one account; repeated signup is rejected; login success establishes a session; missing CSRF is forbidden.

```java
mockMvc.perform(get("/login")).andExpect(status().isOk())
    .andExpect(content().string(containsString("Entrar")));
mockMvc.perform(post("/register").with(csrf())
    .param("name", "Teste").param("email", "owner@example.test")
    .param("password", "phrase-for-local-test").param("confirmPassword", "phrase-for-local-test"))
    .andExpect(status().is3xxRedirection());
```

- [x] Run `.local-tools/apache-maven-3.9.11/bin/mvn.cmd -q -Dtest=RegistrationSecurityTest test`; confirm missing features cause failures.
- [x] Implement transactional owner claim, normalized email, hash cost 12, bounded login attempts, five-minute lockout, generic failures, POST logout and CSP.
- [x] Verify success, invalid input, concurrent first registration, failed-login lockout, session and no secret echo.

## Task 3: Migration and enforced workspace access

Files: db/migration/V3__workspace_ownership.sql; domain entities/services/repositories for contacts/templates/SMTP; dashboard/DashboardService.java; tests security/WorkspaceIsolationTest.java and migration/WorkspaceMigrationTest.java.

Interfaces: entity constructors consume workspaceId; repositories expose `findByIdAndWorkspaceId`; existing service public methods derive trusted workspace internally.

- [x] Write failing tests with A/B principals and real persistence: B data never appears in A lists, counts, updates, delete, diagnose or send.
- [x] Add workspaces/app_users, reserved workspace and transactional backfill; compose domain uniqueness and FKs with workspace_id.
- [x] Update services, queries, counts and secret reveal with ownership checks.
- [x] Run isolated PostgreSQL migrations V1/V2 plus legacy fixtures then V3; assert preserved IDs/counts, rejected cross-workspace links and per-workspace uniqueness.

```java
assertThatThrownBy(() -> service.get(otherWorkspaceContactId))
    .isInstanceOf(EntityNotFoundException.class);
```

## Task 4: Interface, account setup and SMTP safety

Files: templates/fragments/navigation.html; existing templates; static/css/app.css; static/js/navigation.js; SMTP form/controller/service; tests settings/smtp/{SmtpSetupSecurityTest,SmtpTransportSecurityTest}.java.

- [x] Keep one sidebar with working destinations and collapsible Mais recursos, accessible mobile disclosure, active link and POST logout.
- [x] Use Meu e-mail and Modelos de mensagem labels, simple email-first form and advanced settings.
- [x] Test forged existingSecret, TLS enforcement, SMTP error redaction and cross-workspace account use before changing behavior.
- [x] Resolve provider presets in backend and validate actual stored secret rather than client hidden fields.
- [x] Verify UI with rendered forms, browser desktop/mobile and keyboard, then fix material issues in one batch.

## Task 5: Verification and independent review

- [x] Run focused tests, all tests and package; record actual outcomes.
- [x] Review SQL migration with PostgreSQL; never report H2 as migration verification.
- [x] Run five independent juror passes on the same final state; fix approved security/correctness issues and rerun affected tests.
- [x] Update README with start/setup procedure, delivered scope and remaining send/automation increments.

## Execution record

2026-09-08: User authorization confirmed from repeated explicit requests. Baseline source archive SHA256 4F3A7860F9550AE2E50A63BF9AA28DF0941AB3503C8DEC8506FCCAA213D9EDA7. No PostgreSQL server or application listener observed on 5432/8080; no live database migration attempted. Higgsfield generation tools unavailable in this session; no asset generation performed. Broad deep scan from earlier turns has no verified completion result and is not evidence of security clearance.

2026-09-09: Tasks 1–5 completed for the defined foundation increment. Baseline: 12 passing tests. Final Maven package: exit 0, 45 tests, zero failures/errors/skips. PostgreSQL 14.22 temporary databases verified all 19 legacy tables, 15 inter-workspace references, delete semantics, concurrent duplicate handling and persisted-workspace access. Full browser flows passed in Chrome at 1366x900, 390x844 and 320x740, without console errors or page overflow. Five independent reviewers completed an initial pass and a targeted follow-up; accepted residual risks are documented in docs/SECURE-FOUNDATION-REVIEW.md. Disposable preview and its database were shut down; no personal database or real email provider was used.

Final artifact: target/mailflow-local-0.1.0-SNAPSHOT.jar, 66,484,664 bytes; SHA256 D242BAC9D6676093EE3F206506702DB9AD67548085BED186F35B9BE1F1FF34BD.

Scope adjustments: Outlook requires future OAuth2 and is explicitly deferred; no nonfunctional password-based preset is offered. V3 preserves legacy case-sensitive blocked-address uniqueness to avoid data loss/migration failure; future suppression lookups must be case-insensitive. Password recovery and automatic sending/scheduling remain outside this increment. No claim of complete SaaS readiness or absence of vulnerabilities.
