# Local delivery and scheduling implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans task-by-task. Existing user approval covers increments C and D; do not repeat the design gate.

**Goal:** Finish the personal local sending workflow: compose, review, optional self-test, confirm, schedule, pause/cancel and inspect each result.

**Architecture:** Keep Spring MVC/Thymeleaf and PostgreSQL. Extend the existing messages, message_recipients and delivery_jobs tables; only explicitly confirmed new-flow messages enter the worker. Persist immutable recipient/content snapshots and all finite recurrence occurrences. Claim jobs transactionally; never automatically retry an ambiguous SMTP outcome.

**Tech Stack:** Java 25, Spring Boot, JDBC transactions, Flyway, Jakarta Mail, jsoup HTML allowlist, JUnit, embedded PostgreSQL, Playwright.

**Spec:** ../specs/2026-09-04-secure-mailflow-evolution-design.md (increments C/D).

**Estado final (2026-09-14):** executado. O pacote final passou em 77 testes; o fluxo sintético completo passou em Chromium desktop e mobile. Evidências e limitações estão em `../../LOCAL-DELIVERY-REVIEW.md`.

## Global constraints and quality contract

- Rigoroso: test first, PostgreSQL concurrency, hostile input, browser desktop/mobile, independent review and final package.
- Localhost only; no hosting, payments, OAuth, marketing tracking, mass sends or external messages during development.
- Preserve existing files and data. No production database migration, commit or push in this task. V4 is verified on disposable databases.
- Existing branch codex/secure-local-foundation contains the user's untracked implementation; continue in place, preserving that baseline.
- Maximum 20 recipients per message, 30 daily/weekly occurrences, 12 transport attempts/minute and 200/day locally. These are application safety limits, not provider quotas.
- UTC storage, user-selectable IANA timezone, reject invalid/ambiguous wall-clock times. Finite recurrences preserve local time.
- A due job older than 15 minutes becomes MISSED and requires an explicit release; no catch-up burst. A stale in-flight job becomes UNKNOWN and is never auto-retried.
- SMTP acknowledgement means accepted by provider, not inbox delivery. At-most-once automatic attempt after an uncertain outcome; exactly-once remote delivery is not promised.
- UI extends the incumbent dark design and native forms. No redesign, generated imagery or new frontend framework.

## Task 1 — durable draft and review

Files: db/migration/V4__confirmed_delivery.sql; delivery/DispatchForm.java, DispatchService.java, MessageContent.java; delivery/DispatchIntegrationTest.java, MessageContentTest.java.

- [x] Add real PostgreSQL tests: creating a draft queues zero jobs; confirming twice queues exactly one per recipient/occurrence; foreign IDs return 404; inactive/blocked contacts are rejected; draft uses sanitized and personalized snapshots.
- [x] Run `mvn -q -Dtest=DispatchIntegrationTest,MessageContentTest test` and observe the absent implementation fail.
- [x] Extend legacy tables without changing existing records. `DispatchService.createDraft(DispatchForm)` returns UUID; `confirm(UUID)` changes DRAFT to QUEUED under a row lock, creates unique jobs and audits the transition. `get(UUID)` and `list(String)` derive workspace from CurrentWorkspace.
- [x] `MessageContent.render(subject,text,html,name,email)` substitutes only nome/email, rejects unresolved variables/header injection and sanitizes HTML using a tags-only allowlist (no links, images, attributes or remote content). Preview is isolated and escaped, never unsanitized th:utext.
- [x] Rerun focused tests. No commit without separate authorization.

## Task 2 — protected transport and persistent worker

Files: delivery/EmailGateway.java, SmtpEmailGateway.java, DispatchWorker.java, DispatchQueue.java; delivery/DispatchWorkerTest.java, SecureGatewayTest.java.

- [x] Add tests for one-recipient envelope, TLS downgrade prevention, redacted errors, success, safe pre-connect retry, ambiguous failure without retry, double claim, stale claim, schedule due time, pause/cancel and post-confirmation contact blocking.
- [x] Observe failing tests before implementing.
- [x] `EmailGateway.send(SmtpAccount, EmailMessage)` returns a typed outcome; connect must finish before transmission starts. Connection failures permit at most three attempts with delays; any uncertain transfer becomes UNKNOWN.
- [x] `DispatchQueue.claim()` locks a global guard then one job, checks persisted limits and inserts an attempt before committing. Worker calls the transport outside that transaction; `finish` records the result. Claim only joins confirmed new-flow messages, not legacy jobs. Credentials are resolved only for the job's trusted workspace/account.
- [x] Capture sender destination fingerprint at review; changes require a new review rather than silently sending from a different account. Disabled accounts, changed/deleted/blocked contacts and case-insensitive suppression are skipped safely.
- [x] Poll persisted queue every five seconds. Tests disable polling and call the same worker directly. Verify restart and concurrent claims on real PostgreSQL.

## Task 3 — complete browser workflow

Files: delivery/DispatchController.java; templates/delivery/{form,review,list}.html; fragments/navigation.html; dashboard.html; static/css/app.css.

- [x] Add MockMvc tests for authenticated routes, CSRF, review/confirm/test/cancel/pause/resume, invalid fields and foreign workspace access.
- [x] Build New send form (contact selection, optional template loading, editable content), immutable review with own-address test, final confirmation, date/time/timezone and bounded daily/weekly repetition.
- [x] Provide Agendamentos/Automações lists, per-recipient history, safe retry/release only for known non-accepted or missed jobs; unknown results explain manual mailbox review without a retry button.
- [x] Validate native form error recovery, mobile navigation, keyboard, no overflow or external preview loads in one batched browser round and one confirmation round.

## Task 4 — release evidence

Files: README.md; docs/LOCAL-DELIVERY-REVIEW.md.

- [x] Run full tests and package; inspect packaged migration/resources and archive checksum.
- [x] Correctness/security/UX review, fix evidenced in-scope defects with regression tests; clearly distinguish local review from any unavailable formal security scan.
- [x] Document initialization, limits, missed schedules, restart uncertainty, finite recurrence, actual verification and residual risks. Stop test servers. No claim of public-hosting readiness or real-provider delivery without such tests.

## Source checks

- jsoup sanitizer dependency: https://jsoup.org/news/release-1.22.2
- TLS and SMTP partial delivery properties: https://eclipse-ee4j.github.io/angus-mail/docs/api/org.eclipse.angus.mail/org/eclipse/angus/mail/smtp/package-summary.html
