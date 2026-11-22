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

**Why:** _(add your own reasoning here)_

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

**My review:** _(accept / challenge / override each point above)_

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

**Why:** _(add your reasoning here)_

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

**My review:** _(add here)_

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

**My review:** _(add here)_

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

**My review:** _(add here)_
