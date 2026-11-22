# AI Working Journal

A running log of how AI (Claude Code, model Claude Opus 5.5) was used on this project:
what I asked, what I accepted, what I challenged, and what I overrode.

Legend: ✅ accepted · ⚠️ challenged · ❌ overrode

---

## Entry 1: Reading the brief and choosing the architecture
**Asked:** Read the brief and summarise the project, its structure and features. Recommend options, but make no decisions without asking me.

**AI proposed:**
- Core entities: Policy, Claimant, Claim, StatusHistory, InformationRequest, Decision, Staff.
- Claim state machine: `SUBMITTED → UNDER_REVIEW ⇄ AWAITING_INFO → APPROVED → SETTLED`, or `→ REJECTED`.
- REST for commands and queries the user waits on; Kafka for domain events that feed notifications and the workload/exposure read model.
- Priority order: domain → REST API → Kafka → auth → Angular → docs.

**My decisions:**
| # | Question | Options offered | Chose |
|---|----------|-----------------|-------|
| 1 | Backend stack | Java/Spring Boot vs C#/.NET | ✅ Java/Spring Boot |
| 2 | Service count | A: one service, Kafka consumers as internal modules · B: two services | ✅ A |
| 3 | Database | Postgres in Docker vs H2 | ✅ Postgres in Docker |
| 4 | Auth | Seeded users + simple JWT vs Keycloak | ✅ Seeded users + JWT |
| 5 | API contract | Code-first (generated OpenAPI) vs contract-first | ✅ Code-first |
| 6 | Journal | Start now | ✅ Yes |

**Why:** I chose Spring Boot because Java was already installed and I have learned Java in my university studies before. I picked one service because of the limitation of time.

---

## Entry 2: Repository setup
**Asked:** How to create the GitHub repo and link it.

**AI did:** `git init`, added a `.gitignore` that excludes the assessment brief and my personal AI instructions, made the first commit, linked the remote and pushed.

**Notes:** ✅ Accepted keeping the brief out of a possibly public repo.

---

## Entry 3: Backend foundation
**Asked:** Build the foundation from the decisions above.

**AI did:**
- `docker-compose.yml`: Postgres 17 + Kafka 4.1 (KRaft, no ZooKeeper).
- Spring Boot 4.1.3 service scaffold with the Maven wrapper (generated in a Maven container, since Maven isn't installed locally).
- Flyway schema (`V1`) and demo seed data (`db/seed`, loaded only under the `local` profile, so it can never reach other environments).
- Entities: `AppUser`, `Policy`, `Claim`, `ClaimStatusHistory`, `InformationRequest`. The lifecycle rules live in `ClaimStatus`; `Claim.changeStatus` enforces them and writes history.
- 24 unit tests on the state machine and the claim rules: all passing. The app starts, migrations apply, and `/actuator/health` reports UP.

**AI changed from its own earlier proposal (flag for review):**
- Dropped the separate `Decision` table: decision fields live on `Claim`, since a claim has one decision at most.
- Two roles (`CLAIMANT`, `STAFF`), with no manager role and no teams table.
- Initial reserve = claimant's estimate, capped at the coverage limit.
- Demo seed data runs through Flyway (`db/seed`), so the six markets and a demo password live in the repo.

**Issues hit and fixed:**
- Hibernate's schema check rejected `CHAR(3)` for currency → changed to `VARCHAR(3)`.
- Port 8080 is taken by a local Oracle listener → the backend runs on 8081.

**My review:** Reviewed in Entry 5. I learned I should always ask AI to suggest me before it builds.

⚠️ **Challenged the AI's process:** it made the decisions above on its own and only reported them afterwards. I told it to stop deciding for me; from now on it puts every choice to me as options first.

---

## Entry 4: Basic Angular screen first (reordered the plan)
**Asked:** After running the backend, I couldn't see any interface. ❌ **Overrode the AI's backend-first order:** I asked for a simple Angular screen before the REST API.

**AI asked first, I chose:**
| Question | Chose |
|----------|-------|
| Which screen | ✅ App shell + staff work queue |
| Data source (no API yet) | ✅ Mock data in Angular, behind a `ClaimService` to be swapped for HTTP |
| UI library | ✅ Angular Material |
| Setup | ✅ Angular 22, standalone, no SSR, SCSS, in `frontend/claims-web` |

**AI did:** a top bar with a Staff/Claimant switch, a lazy-loaded work queue table (claim, claimant, type, market, status pill, officer, reserve, submitted), and a placeholder claimant page. The build and its 1 test pass.

**Why:** Simple screen with mock data help me understand the flow clearly instead of running the backend only I couldn't see anything.

---

## Entry 5: Backend review before the first commit
**Asked:** Help me review the backend before committing. The AI listed every decision baked into the code (B1–B15 below), each with its reason and alternative.

**My verdict:** ✅ Kept all of them.
- Domain: lifecycle without withdraw/appeal (B1), reserve = estimate capped at coverage (B2), decision fields on Claim (B3), CLAIMANT + STAFF roles (B4), assumed six markets (B5), per-policy currency with no FX (B6).
- Technical: UUID + readable claim number (B7), optimistic locking (B8), Flyway owns schema (B9), text + CHECK status columns (B10), package-by-domain (B11), no Lombok (B12), Spring Boot 4.1.3 (B13), port 8081 (B14), demo seed under `local` profile (B15).

**Known gaps carried forward:** assign/approve/reject/reserve-revision logic not built yet; the incident-date-within-policy check (`Policy.covers`) is unused.

**Also decided:** split the work into separate commits (backend, frontend, journal) for a readable history.

**Also raised:** the claimant screen has no "Report an incident" form yet. The form's design questions (real API vs mock, fields, validation, what happens after submit) are still open.

---

## Entry 6: Claimant "Report an incident" (first real API)
**Asked:** Build the claimant's first step, reporting an incident. The AI asked me two rounds of questions, then showed a numbered build plan (P1–P19), which I approved before any code was written.

**My decisions:**
| Question | Chose |
|----------|-------|
| Real API or mock | ✅ Real API, saved to Postgres |
| Form fields | ✅ Policy, incident date, description, estimated loss |
| Validation | ✅ All four: required + amount > 0, date not in future, date within policy period, own policies only |
| After submit | ✅ Back to My claims, new row at the top, confirmation message |
| Identity before JWT | ✅ Temporary `X-User-Id` header with a "Viewing as" picker (a stub, replaced by JWT later) |
| Estimate above coverage limit | ✅ Allow; reserve capped at the limit |
| Description minimum | ⚠️ **10 characters** (the AI recommended 20) |
| Staff queue | ✅ Switch to real data |

**AI built:**
- Backend: `GET /api/me/policies`, `POST /api/claims`, `GET /api/me/claims`, `GET /api/claims` (staff), `GET /api/dev/users` (local profile only). Role checks on every endpoint. Errors in RFC 9457 Problem Details format with per-field messages. Swagger UI at `/swagger-ui.html`.
- Tests: 47 passing, including 9 integration tests against a real Postgres (Testcontainers). They cover submit, the reserve cap, each validation rejection, and 401/403 role checks.
- Frontend: "Viewing as" picker with role-based routes and guards, My claims page, the report form (same checks as the backend; server errors shown on the matching field), and the staff queue on real data. The mock data was deleted.

**Issues hit:**
- My old backend instance was still running on 8081 with old code → I stopped it myself.
- A stale Angular build cache caused an injector error → cleared.
- The button's "+" icon was invisible (icon font mismatch) → removed.
- The AI's first version of the policy-date validator had a leftover hack → it caught and rewrote it before review.

**Verified in the browser:** empty form shows all errors → valid submit as Alice creates CLM-000006 → it appears at the top of her list → switching to Priya shows it in the queue with the reserve capped at SGD 50,000.

**Choices inside the approved plan, flagged for review:** the picker remembers the user across refreshes; it defaults to the first user (Alice); the confirmation shows for 5 seconds.

**Known items:** the bundle is 596 KB against Angular's 500 KB default budget (a warning, not an error). The server-error-on-field display is covered by backend tests but wasn't clicked through in the browser.

⚠️ **Challenged:** I asked why the form showed SGD and why Alice's limit was 50,000, and where the rules were. The answer: currency and limit are **demo data the AI invented** in the seed file, and the reserve cap is a rule in `Claim.submit`. The rules were spread across files with no single list, so I had the AI add a **Business rules** section to `docs/decisions.md` (rule → code → test → who decided), including the gaps it found (nothing links market to currency; one limit per policy).

**Second review (items R1–R10):** I asked the AI to explain R8–R10 clearly before deciding.
- R1–R7 (picker memory, default user, 5-second message, "Policy not found", trimming, no description in lists, shared status pill and route guards): ✅ kept.
- R8, the `Location` header pointed to `/api/claims/{id}`, which didn't exist: ❌ **overrode the AI's recommendation** (remove the header) and chose to **build the endpoint now**. Then I decided: it returns detail + status history; someone else's claim → 404; API only, no UI yet. 5 new integration tests, including following the `Location` header after submit.
- R9, bundle size warning (596 KB vs 500 KB): ❌ **overrode the AI's recommendation** (raise the budget) and **left it as is**.
- R10, lists not paginated: ✅ documented as a known gap; paging comes with the work-queue filters.

Tests: 52 backend, 2 frontend, all passing.

**My review:** I found out there are invented demo data so I asked for business rules list. I overrode R8 because a link to a missing endpoint is a broken promise in API, so I preferred to build the endpoint.
---

## Entry 7: Staff actions
**Asked:** Build the staff side: pick up, assess, settle or reject. The AI asked two rounds of questions, showed a build plan with its own proposals marked ❓ (S8–S15, U4), and then, mid-build, stopped to ask three more questions it found while mapping the code.

**My decisions:**
| Question | Chose |
|----------|-------|
| Actions in scope | ✅ All: pick up + revise reserve, approve + settle, reject, request info |
| Assignment | ✅ Self pick-up only (no reassignment) |
| Who acts | ✅ Only the assigned officer |
| UI | ✅ Staff claim detail page |
| Approved amount | ✅ > 0 and ≤ coverage limit |
| Settle | ✅ Paid = approved amount |
| Claimant's answer | ✅ Build a minimal answer page now (otherwise claims get stuck in Awaiting info) |
| Text rules | ✅ 10–2000 characters for reason, question and answer |
| Plan proposals S8–S15, U4 | ✅ Approved all: reserve 0..limit; reserve audit deferred (gap); reserve follows decisions; 409/403/404 codes; first pick-up wins; extra detail fields; filters now, paging later; Kafka later; confirm irreversible actions |
| Mid-build: check order | ✅ Ownership (403) before status (409) |
| Mid-build: history note | ✅ Copy the question and rejection reason into history notes |

**AI built:**
- Backend: 7 action endpoints (`pick-up`, `reserve`, `request-info`, `answer`, `approve`, `reject`, `settle`), each enforced in the `Claim` domain object; a queue filter; detail now includes the officer id, coverage limit, decision fields and Q&A.
- Frontend: queue filter + clickable rows; a staff claim page with status-aware buttons; one shared action dialog (with "This can't be undone." on approve/reject/settle); a minimal claimant answer page.
- Tests: 79 backend (17 domain unit tests for the actions, 15 new integration tests including the database regression test), 2 frontend.

**Bugs the AI introduced and the tests caught (before any browser check):**
1. **Empty history rows.** For an existing claim, the AI's code called `saveAndFlush`, which makes JPA "merge" the claim, and new history entries were copied as empty rows (`claim_id` null). Five integration tests failed on the database constraint. Fix: the claim is already managed inside the transaction, so it only needs `flush()`.
2. **Test setup.** After the AI moved the Postgres container into a shared base class, the second test class failed: JUnit stopped the container after the first class while Spring reused its cached connection. Fix: the standard "singleton container" (started once for the whole run).

**My decision on the bugs:** I asked the AI to explain both, with alternatives, before deciding.
- Bug 1: ✅ kept `flush()` (the alternative was cascading merges, which hides the cause) and ✅ **added a regression test that reads the database tables directly** (`actionsWriteCompleteHistoryAndInformationRequestRows`). The API-level tests only see the in-memory object; this one checks the stored rows.
- Bug 2: ✅ kept the singleton container (the alternatives were restarting Spring per class, or duplicating the setup).
- **Proof the test works:** with the old `saveAndFlush` line put back, the new test fails with the original error (`null value in column "claim_id"`); with the fix, all 79 tests pass.

**Verified in the browser:** Priya picks up CLM-000006 → requests info → Alice answers on her page → Priya approves (an over-limit SGD 60,000 was blocked; SGD 3,800 accepted, reserve became 3,800) → settles (reserve 0, no buttons left). Daniel opening Priya's claim sees it read-only.

**Issues along the way:** the old backend was still running on port 8081 (I stopped it myself after the AI's attempt was blocked by a permission check); the dev server's dependency cache went stale after new Material modules were added → cleared and restarted.

**Review (items V1–V9):** I asked the AI to explain V7–V9 before deciding.
- V1–V6 ✅ kept: dialogs start with the current reserve; the settle confirmation shows the amount; the claimant answers the latest open question; only Awaiting info rows are clickable for claimants; texts are trimmed; the extra test refactor and the unused library removal stay.
- V7, the Reject label said "Reason (shown to the claimant)", but claimants can't see the reason yet: ✅ **changed to "Reason"**. A code comment marks it, to restore when claimant tracking shows the reason.
- V8, the work queue flashed "No claims." while loading: ✅ **shows nothing until loaded**, the same pattern as My claims. Verified: no flash on a fresh load.
- V9, claimants can't see decisions (approved amount, rejection reason): ✅ **left for the claimant tracking milestone**, which should come next, since the brief requires claimants to "receive decisions".

**My review:** The test caught 2 AI bugs before I saw anything in the browser. So I asked for database-level test which the bug cannot come back silently.

---

## Entry 8: Claimant tracking
**Why:** While testing, I rejected Ben's claim as Priya, then switched to Ben, and he couldn't see the reason. The AI confirmed this was the V9 gap: the reason was saved and the API already returned it, but no claimant screen showed it.

⚠️ **The AI also challenged my test:** my reason ("Please provide more evidences to prove") reads like a request for information, and Rejected is final (B1), so Ben can't send that evidence now. This shows why Request info and Reject are separate actions, and it's a likely panel question ("what if the claimant disagrees?" → no appeal flow, a known simplification).

**My decisions:**
| Question | Chose |
|----------|-------|
| Page | ✅ One full claimant claim page (replaces the minimal answer page) |
| Timeline | ⚠️ Statuses + dates **with staff names** (the AI recommended hiding names as "Claims team") |
| Internal data | ✅ Separate claimant response; the reserve and officer id never leave the server |
| Updates | ✅ Seen when the app is opened (live push comes with Kafka) |
| Officer name on page | ✅ Yes |
| Notes in timeline | ✅ Yes |
| Coverage limit | ❌ **Show it** (overrode the AI's recommendation to hide it) |
| List API | ✅ Trimmed too |
| Plan proposals C2–C3, F3–F5 | ✅ Approved: new `GET /api/me/claims/{id}` (staff-only `GET /api/claims/{id}`); submit and answer also return the claimant format; decision wording; oldest-first timeline; restore the Reject label |
| After answering | ✅ Stay on the claim page (the old page went back to My claims) |

**AI built:**
- Backend: `ClaimantClaimSummary` and `ClaimantClaimDetail`, the claimant claim endpoint, claimant formats for submit, list and answer, and the Location link moved to `/api/me/claims/{id}`. 82 tests, including checks that no claimant response contains `reserveAmount` or `assignedOfficerId`, and that each role is blocked from the other's URL.
- Frontend: a full claimant claim page (decision, details, Q&A with an answer box, timeline); every My claims row opens it; the Reject label is back to "Reason (shown to the claimant)".

**Verified in the browser:** Ben opens CLM-000002 and sees "Rejected" with my reason, plus a timeline with names; his browser received no reserve or officer id (checked the raw responses). Alice answers the seeded question on CLM-000003 and stays on the page, now Under review. CLM-000006 shows "Settled: SGD 3,800.00 paid". Staff still get the reserve from the staff URL.

**Issue along the way:** the old backend was still on port 8081 again → I stopped it.

**Review:** the AI listed every change (features, API before/after, files, tests). Beyond-plan items: ✅ X1 keep `CurrentUserService.require(userId)` private (no longer used outside the class); ✅ X2 keep the shared timeline/Q&A helpers, so the staff and claimant views can't drift apart.

**My review:** I chose to show staff names and coverage limit because I think claimants should understand who is handling their claims and why an amount might be lower for reminder purpose.

---

## Entry 9: Team workload and performance dashboard
**Asked:** The brief says staff should "see their team's workload and performance", and asks what the system needs to know about outstanding liability exposure. ❌ I chose this **before the README**, which the AI had recommended doing first.

**My decisions:**
| Question | Chose |
|----------|-------|
| Measures | ✅ All four: workload per officer, unassigned queue, liability exposure, performance per officer |
| Data source | ✅ Live SQL queries (Kafka read model later) |
| Period | ✅ All time |
| Currency | ✅ Separate total per currency (no FX) |
| Exposure breakdown | ✅ Total + by market + by product |
| Timings | ⚠️ First chose submission → pick-up and approval → settlement. The AI pointed out that pick-up → decision was left out (a likely panel question), so I **added it** |
| Placement | ✅ Tab next to Work queue |
| Display | ✅ Number cards + tables (no chart library) |
| Plan proposals D1, D3, D5–D7, F1, F5 | ✅ Approved: one endpoint, idle officers as zeros, counts from history, timing definitions, hours/days display, staff tabs, "–" for no data |
| Duplicate heading | ✅ Removed the "Work queue" heading, since the tab already names the page |

**AI built:**
- Backend: `GET /api/dashboard` in a new `dashboard` package; 4 SQL aggregate sections (JdbcTemplate). 88 tests: `DashboardIntegrationTest` checks **exact numbers hand-calculated from the seed data** (e.g. SGD exposure 29,200, Priya's pick-up → decision 78.6 h, Daniel's 35 min), checks that settling updates exposure and counts, and checks that claimants get 403. All matched on the first run.
- Frontend: staff tab bar (Work queue / Team dashboard), a dashboard page with an Unassigned card, an exposure card per currency (market and product tables), and Workload and Performance tables; a `duration` pipe for hours/days.

**Caveat documented:** pick-up → decision includes time spent waiting for the claimant, so it measures total assessment time, not only officer effort.

**Verified in the browser:** the dashboard showed the local (test-modified) data, and every figure was cross-checked against the actual claim states, including recalculating the averages by hand. The tabs switch, the work queue has no duplicate heading, and there are no console errors.

**My review:** When the AI pointed out that I'd left out pick-up → decision, I added it, because 'how fast do officers decide' is the most obvious performance question. I accept that it includes time waiting for the claimant

---

## Entry 10: README
**Asked:** Write the README. ❌ I chose it **before Kafka** (the AI had suggested Kafka first, so the README could describe the finished system).

**My decisions:**
| Question | Chose |
|----------|-------|
| Sections | ✅ All: quick start + demo walkthrough, features by role, architecture + REST vs Kafka, testing / structure / API / links |
| Diagram | ✅ Mermaid (GitHub draws it from text) |
| Kafka | ✅ State it plainly as "planned, not yet wired", with the intended REST vs Kafka split |
| Length | ✅ Concise, linking to `docs/decisions.md` and this journal |
| Sign-in | ✅ Explain the "Viewing as" picker only; don't mention the unused seed passwords |
| Reset instruction | ✅ Include `docker compose down -v` |
| Title | ✅ "Claims Platform", with Chubb APAC named in the intro (not presented as an official Chubb product) |

**AI checked the README against the running system:** Swagger, OpenAPI and health URLs all return 200; the endpoint table matches the 14 endpoints in the generated OpenAPI spec (the dev-only user list is left out on purpose); `npm start` uses the proxy; the reset command matches the compose volume. It corrected one inaccurate line ("one claim in each status": the seed has no Settled claim).

**Mermaid diagrams:** automatic checking before pushing was blocked by a permission check. ✅ **Verified on GitHub after pushing:** both the lifecycle and the architecture diagram render correctly, with no raw diagram text or errors on the page.

**My review:** I did the README before Kafka because a reviewer who can't start the app may never look at the code.

---

## Entry 11: Kafka events, Part 1 (outbox, audit log, notifications)
**Asked:** Use Kafka, which the brief asks for ("REST/HTTP and Kafka. You decide what uses which"). The AI asked two rounds of questions, showed a plan with its proposals marked ❓ (K1–K12, F1–F3), and stopped mid-build to ask about a database migration problem.

**My decisions:**
| Question | Chose |
|----------|-------|
| What Kafka is used for | ✅ All three: claimant notifications, dashboard read model, audit log |
| Publishing | ✅ Transactional outbox (event saved in the same transaction as the change) |
| Topics | ✅ One topic `claim-events`, keyed by claim id |
| Format | ✅ JSON |
| Notification delivery | ✅ Bell with unread count, refreshed on load/navigation (no live push) |
| Notify on | ❌ **Every status change** (overrode the AI's "only what needs attention") |
| Read model scope | ✅ Liability exposure only |
| Delivery | ✅ Two parts with a review between (this is Part 1; the exposure read model is Part 2) |
| Plan proposals K1–K12, F1–F3 | ✅ Approved, with one change: ⚠️ **K8: no notifications for the claimant's own actions** (submitting, answering) |
| Mid-build: migration ordering | ✅ New tables as V2 and a one-time reset of my local database (the alternatives were Flyway out-of-order mode, or numbering it V101) |

**AI built:**
- `Claim` records an event on each change (after its effects, so the event carries the final reserve); `OutboxWriter` saves events in the same transaction; `OutboxRelay` publishes every second, in order, and marks rows published only after Kafka confirms.
- Consumers in the same service: the audit log (`claim_event_log`, which closes the S9 reserve-audit gap) and notifications (`notification`); duplicates ignored by event id; 3 retries, then the dead-letter topic.
- Notification API (list, mark read, mark all read) and a bell in the top bar for claimants; fixed the icon font (the cause of the invisible "+" icon in an earlier milestone).
- Tests: 101 backend. New: 5 domain event tests, 3 notification-rule tests, 1 event-format test, and 4 end-to-end tests with real Kafka (5 outbox rows all published → 5 audit entries → exactly 2 notifications; a re-sent duplicate changes nothing; a rejected action writes no event; marking read).

**AI mistakes found and how I decided:**
1. **Test mistake:** the end-to-end test matched the stored JSON as text, but Postgres JSONB reformats it (spaces, number scale). The code was correct. I asked for options: ✅ **kept "read the fields and compare as numbers"** (rejected: storing the JSON as text, which changes the production design to suit a test; and whole-object comparison, which is brittle).
2. **Related inconsistency the test exposed:** event amounts mixed formats (`2000.00` from the database, `1500` from a request). ✅ **Normalised every event amount to 2 decimals**, with a test.
3. **Badge:** Material 3's "small" badge is a dot without a number; the plan said "unread count". Corrected during the browser check.

**Verified in the browser** (after my database reset): Priya picks up CLM-000001 → outbox row published → audit log entry → notification. Alice's bell shows "1" and "Priya Nair picked up CLM-000001"; clicking it opens the claim and marks it read. A reserve revision appears in the audit log (before/after, who) but creates no notification.

**Part 2 decision:** ❌ **Skipped Kafka Part 2 (the exposure read model) because of the time cap.** I was close to the brief's 5-hour limit, and live SQL is exact at this scale. Documented as "not built (time cap)" in the README and decisions.md, with how it would be done (running totals from claim events, which already carry reserve before/after).

**My review:** I wanted claimants notified of every change so they always know where their claim is, but not about their own actions, which tell them nothing new. The test mistake showed me that a test should check meaning, not formatting.
