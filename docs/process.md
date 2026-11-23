# Process

How an idea becomes production code, as the workflows and the app do it today. The history of
how it grew is in [plan.md](plan.md); this file is updated at the end of every step and describes
only the current state. Derived from `.github/workflows/*.yml`, `.github/acceptance/` and the app.

## From idea to merge

| # | Who | What | Trigger / result |
|---|-----|------|------------------|
| 1 | Human | Submits title and description in production | Issue with label `refine` |
| 2 | Refine agent (`refine.yml`) | Asks at most 3 short questions per round, or proposes a German Gherkin delta against `main`; speaks the language of the domain, no technical terms | Comment `## Refine`; runs on label `refine` and on every `## Idea chat` comment |
| 3 | Human | Answers in the idea chat (new round), or presses "Freigeben" | Comment `## Idea chat`, or comment `## Acceptance criteria` (copy of the proposal) and label `refine` -> `agent` |
| 4 | Coding agent, phase 1 (`agent.yml`) | Writes only JGiven acceptance tests, stages, fakes, compiling stubs; opens the pull request `agent/issue-<n>` with the demo tour | Label `agent` |
| 5 | `acceptance` | Compares tests with the approved delta, judges changed stage/fake methods | Green: comment `## Acceptance checked <sha>`, starts phase 2. Red: `## Acceptance round <n>`, starts the agent |
| 6 | Coding agent, phase 2 | Writes the production code; the checked tests must stay unchanged | Started by `acceptance`; push |
| 7 | `build`, `acceptance`, `review` | Build and tests; check again; review agent (only after phase 2) | Red review: `## Review round`, starts the agent |
| 8 | Preview (`ci.yml`) | Deploys the pull request to `notes-fourteen-it-issue-<n>` | Preview bar: checks, demo tour, feedback, merge |
| 9 | Human | Walks the tour. Feedback, or merge | Feedback: comment `## Human feedback`, starts the agent. Merge: squash, only with green `build`, `e2e`, `review`, `acceptance` |
| 10 | CI | Deploys `main` to production; `preview-cleanup.yml` removes the preview on close | Push to `main` |

Paths off the main line:

| Case | What happens |
|------|--------------|
| Late conflict: a test of `main` that the delta leaves alone is red after phase 2, or `acceptance` has findings while the branch is behind `main` and does not merge with it (`git merge-tree`). A branch that is behind but merges cleanly gets a normal `## Acceptance round` that tells the agent to merge `origin/main` first | `acceptance` posts `## Acceptance conflict` and dispatches `agent.yml` with `conflict=true`. The agent changes no code, proposes an amended full delta (`## Refine` on the issue) and swaps `agent` for `refine`. After "Freigeben" the new `## Acceptance criteria` replaces the old one; the agent posts `## Acceptance reset` on the pull request and repeats phase 1 on the same branch |
| Feedback that changes approved behaviour or adds a requirement | Same path as a late conflict, started by the feedback comment instead |
| Phase 2 needs a change in tests, stages or fakes | Commit with the line `Phase: 1` at the end; the pull request is back in phase 1 |
| Phase 2 changed the checked tests | `phase.mjs` makes `acceptance` fail; counts as a round |
| Pull request from a branch that is not `agent/issue-<n>` | No phases, no criteria: `acceptance` passes at once, the review runs at once |

## Labels

| Label | Meaning | Set by | Removed by |
|-------|---------|--------|------------|
| `refine` | Idea is in its chat; starts `refine.yml` | App (new idea), coding agent (late conflict, feedback that changes behaviour) | App on "Freigeben" |
| `agent` | Idea is released; starts `agent.yml` | App on "Freigeben" | Coding agent on a late conflict |

An idea is an open issue (no pull request) with one of these labels.

## Comment markers

A marker is the first line of a comment. Only comments by `OWNER`, `MEMBER` or `COLLABORATOR` start
a workflow or count as approval.

| Marker | Where | Written by | Effect |
|--------|-------|-----------|--------|
| `## Idea chat` | issue | App / human | Human's chat message; starts the refine agent while `refine` is set |
| `## Refine` | issue | Refine agent; coding agent for an amended delta | Questions or proposal with a ` ```gherkin ` block. The newest one without a later `## Idea chat` or `## Acceptance criteria` is the pending proposal |
| `## Acceptance criteria` | issue | App on "Freigeben" | The approved delta; the latest one counts, it replaces earlier ones |
| `## Acceptance checked <sha>` | pull request | `acceptance` | Phase 1 done for that commit; from here phase 2 |
| `## Acceptance round <n>` | pull request | `acceptance` | Findings; starts the coding agent. Rounds count from the last `## Acceptance reset` |
| `## Acceptance reset` | pull request | Coding agent workflow, on a re-release | Voids the checked commit and the rounds: phase 1 again |
| `## Acceptance conflict` | pull request | `acceptance` | Late conflict, see above |
| `## Review round` | pull request | Review agent | PASS or FAIL with findings |
| `## Human feedback` | pull request | Preview bar / human | Starts the coding agent; no round limit; only on `agent/*` branches |
| `Preview: <url>` | pull request | `ci.yml` | Link; the one comment starting with `Preview: ` is edited in place |
| `Acceptance failed in <n> rounds...`, `The review failed in <n> rounds...` | pull request | Workflows | The pull request waits for the human |

## Checks

| Check | Job | Required by branch protection | Blocks merge button | Notes |
|-------|-----|------|------|-------|
| `build` | `ci.yml` `build` | yes | yes | `./gradlew build` (with Testcontainers) and the Node tests of `.github/acceptance`. On an agent branch in phase 1 only `./gradlew compileTestJava` (the new acceptance tests are red against the stubs), same phase test as `review`; then no jar |
| `e2e` | `ci.yml` `e2e` | yes | yes | Playwright end-to-end and visual tests (details below), needs `build`. Skipped in phase 1 (no jar); `review` fails there anyway |
| `review` | `review.yml` | yes | yes | Fails without a review until phase 2 has pushed on top of the checked commit |
| `acceptance` | `acceptance.yml` `acceptance` | yes | yes | Passes for branches other than `agent/issue-<n>` |
| `preview` | `ci.yml` `preview` | no | no | Only the deploy; the preview link shows once it is green. Skipped in phase 1 (no jar) |

`build` also checks formatting (`check`): Spotless (google-java-format AOSP style, 4 spaces, unused imports removed, imports sorted) for Java and Prettier (`frontend/.prettierrc.json`) for `frontend/src` and its config files. `./gradlew format` fixes both (`pnpm run format` only the frontend).

`build` also compiles strictly: javac with `-Xlint:all -Werror` (tests without `deprecation`: JGiven's `ScenarioTest`), Error Prone (bug patterns, warnings are errors) and NullAway on main sources. Every package of `de.fourteen.notes` is `@NullMarked` (JSpecify, `package-info.java`); where null is allowed it says `@Nullable`. `OnionArchitectureTest` lets `domain` import `org.jspecify` besides the JDK.

`build` also runs PMD 7 on main sources (`config/pmd/ruleset.xml`: a few errorprone and bestpractices rules, no style rules; tests are not linted). Complexity limits: cyclomatic 10 per method (80 per class), cognitive 15 per method; today's code is below them. `ArchitectureRulesTest` (ArchUnit, compiled classes) adds to `OnionArchitectureTest`: controllers live in `web` and do not use the ports, `NoteRepository` and `GitHub` are interfaces in `domain` implemented only in `infrastructure`, no cycles between rings, `domain` and `application` throw no exception but `DomainException`.

`build` also lints the frontend (`frontendLint`, `pnpm run lint` in `frontend/`): ESLint 10 flat config (`eslint.config.js`) with typescript-eslint type-aware `recommendedTypeChecked`, `react-hooks` (rules of hooks, exhaustive deps; not the React Compiler rules), `jsx-a11y` recommended, `eslint-config-prettier` (formatting stays with Prettier) and `complexity` at 10 per function (same as PMD's cyclomatic limit; JSX `&&` and `?:` count, so big components are split). knip finds unused files, exports and dependencies. `tsc --noEmit` (strict) stays in `pnpm build`. typescript-eslint does not support TypeScript 7 yet, so `typescript` in `package.json` is the TS 6 API package for the linters and `typescript7` provides the `tsc` binary; drop the alias once typescript-eslint supports TS 7.

`build` also tests properties (jqwik 1.10.2 on the JUnit platform, `*PropertiesTest` next to the unit tests): round trips and invariants of `AcceptanceCriteria` (gherkin block, `@ersetzt`/`@entfällt` titles), `IdeaStatus`/`Checks`, `ChatMessage`, the demo block of `PreviewService` and the `DATABASE_URL` mapping (user and password with special characters). It found that a host Java's `URI` cannot parse (e.g. `my_db`) became `jdbc:postgresql://null/...`; it is rejected now. Plain unit tests cover the services (`application`, Mockito on the ports).

`build` also runs mutation tests (`pitest` is part of `check`: PIT 1.30.2, pitest-junit5-plugin 1.2.3, gradle-pitest-plugin 1.19.0) on `de.fourteen.notes.domain.*` and `application.*`, with only the tests in those packages (no Spring, no Testcontainers, no acceptance tests): about 50 seconds. Score today 100 % (91 mutants), `mutationThreshold` is 95 in `build.gradle`; the HTML report is in `build/reports/pitest`. A surviving mutant means a missing test, not a reason to lower the threshold.

`build` also validates the configuration at startup: `GitHubProperties` is `@Validated` (spring-boot-starter-validation): `github.api-url` must be an http(s) URL and `github.repository` `owner/name`, else the app does not start (`GitHubPropertiesTest`). The token stays optional (local runs, tests), as do `PULL_REQUEST`, `PULL_REQUEST_SHA` (previews only) and `DATABASE_URL`, which `DatabaseUrlEnvironmentPostProcessor` already checks. `/actuator/health` is public and shows only the status, no details (`NotesApiTest`); the JDBC check is part of it.

After `flyctl deploy`, `deploy` and `preview` run `.github/smoke.sh <app>` (no secrets, GETs only): polls `https://<app>.fly.dev/actuator/health` until it says `"status":"UP"` (30 x 5 s, the machine starts on the first request), then expects `/` without login to answer 401. A failure fails the job (for `preview` the link comment is then not posted).

`build` also runs `.github/security-static.sh` (Docker, pinned images, the same script locally, about 15 s, also in phase 1): gitleaks 8.28.0 over the whole git history (`.env` is ignored and not tracked), Semgrep CE 1.140.0 with the registry rulesets `p/java`, `p/typescript`, `p/react`, `p/dockerfile`, `p/github-actions` (`--error`, no login, `--metrics=off`; rulesets are fetched at run time, so new rules can turn the build red without a change; `p/secrets` is left to gitleaks) on `src`, `frontend/src`, `Dockerfile` and `.github`, and Trivy 0.67.2 `config` on `Dockerfile` (HIGH, CRITICAL fail; it does not read `fly.toml`). The same script then runs actionlint 1.7.7 (with shellcheck on the `run:` blocks; `.github/actionlint.yaml` ignores SC2016 in `acceptance.yml`, a jq variable) and zizmor 1.16.0 (offline, no token, default persona) on `.github/workflows`. Suppress a zizmor finding inline with `# zizmor: ignore[<rule>] <reason>` (used for `artipacked` in `agent.yml`, which pushes, and for `dangerous-triggers`/`github-env` in `preview-cleanup.yml`). Every `uses:` is pinned to a full commit SHA with the version as a comment (Semgrep `github-actions-mutable-action-tag` is on; update the SHA and the comment together, Dependabot-style by hand), and every checkout that does not push sets `persist-credentials: false`. `build` validates `gradle/wrapper/gradle-wrapper.jar` with `gradle/actions/wrapper-validation` before Gradle runs. Suppress a Semgrep a false positive with `// nosemgrep: <rule-id>` and a reason.

`build` also runs `.github/supply-chain.sh` after `./gradlew build` (Docker, not in phase 1, the same script locally; one named Docker volume `trivy-cache` holds Trivy's database): `cyclonedxDirectBom` (CycloneDX Gradle plugin 3.6.1, runtime classpath only) and `pnpm sbom` (production dependencies, spec 1.6) write `build/sbom/java.cdx.json` and `frontend.cdx.json`, uploaded as the artifact `notes-sbom` (the deploy jobs do not use it). Trivy 0.67.2 `sbom` scans both: vulnerabilities HIGH, CRITICAL with a fix (`--ignore-unfixed`) fail, and so do licenses classified restricted (GPL, LGPL), forbidden (AGPL, SSPL) or unknown; notice (Apache, MIT, BSD) and reciprocal (EPL, MPL, CDDL) pass. Two ids are ignored in the script: `LGPL-2.1-only` (logback, EPL-2.0 or LGPL) and `GPL-2.0-with-classpath-exception` (jakarta.annotation-api, EPL-2.0 or GPL with exception). An ignore list for a CVE that cannot be fixed is `.trivyignore` in the repository root (one id per line, a comment with reason and expiry; none today). Then the image is built from the `Dockerfile` (never pushed) and scanned with Trivy `image` (OS packages and the jar, HIGH, CRITICAL, with a fix). The base image `eclipse-temurin:25-jre` is not pinned by digest: every build gets the current tag, so OS fixes arrive by themselves; the price is that a new base image can turn `build` red without a change here.

The job `e2e` (`ci.yml`, needs `build`, takes its jar, not in phase 1; `deploy` waits for it; required check `e2e`, the app counts a skipped run as red) runs the end-to-end tests with Playwright 1.65.1 (`@playwright/test`, `@axe-core/playwright`; `frontend/e2e/`, `./gradlew e2eTest` = `pnpm run e2e`; not part of `check`, because it needs the jar and three browsers; in CI its own job next to the rest of the pipeline, about 2.5 minutes; run with `-x bootJar`, so the jar of `build` is used). `e2e/stack.mjs` (Playwright's `webServer`) starts a `postgres:17-alpine` container, a stub of the GitHub API (`e2e/github-stub.mjs`, in memory, `POST /__reset` between tests; the real API is never called) and the built `build/libs/notes.jar` twice: production (port 18080) and a preview app (`PULL_REQUEST=7`, port 18081), login dev/dev. Projects chromium, firefox and webkit, one worker (shared database). Tests, in German UI terms with role and label selectors: write a note, input empty after saving (the `@manuell` scenario "Das Eingabefeld ist nach dem Speichern leer" is covered here, the acceptance test is unchanged), order, reload, delete, login required; the ideas dialog, list, new idea and chat; the preview bar (checks, tour, feedback); the JGiven report at `/jgiven/index.html` behind the login (`/jgiven/` alone is a 404). `accessibility.spec.ts` scans the notes view, the ideas list and chat and the preview bar with axe (WCAG 2.0/2.1 A and AA including color contrast) in every browser. `knip.json` ignores the `java` binary of `stack.mjs`; `pnpm run format` covers `e2e/`, ESLint and knip only `src` (the specs are not linted). Visual regression (`./gradlew e2eVisualTest` = `e2e/visual.sh`): `toHaveScreenshot` of the notes view and the ideas dialog in chromium with mocked `/api` and the built `frontend/dist`, only inside the pinned image `mcr.microsoft.com/playwright:v<version>-noble` (`linux/amd64`, like CI), where the snapshots in `e2e/visual/main.spec.ts-snapshots` were made; outside it the test is skipped. After an intended visual change: `pnpm run e2e:visual --update-snapshots` (in `frontend/`) and commit the PNGs. On failure CI uploads the Playwright HTML reports as the artifact `playwright-report`.

Branch protection on `main`: pull request, the four required checks (`build`, `e2e`, `review`, `acceptance`), admins exempt. The merge
button repeats the check on the exact commit, squash merge.

## Round limits

| What | Limit | Then |
|------|-------|------|
| Refine questions | 3 per round (the agent proposes after at most 3 rounds, never releases) | A text answer starts another round |
| `## Acceptance round` | 3, counted since the last `## Acceptance reset` | Comment, the pull request waits |
| `## Review round` | 3 in total on the pull request, never reset | Comment, the pull request waits |
| `## Human feedback` | none | |
| Agent turns | idea 60, fix 40, feedback 40, conflict 30, refine 20, review 30 | |

## Secrets per job

Names only. The GitHub token is the job's `github.token` with the permissions listed.

| Job | Secrets | Token permissions | Runs code of the pull request |
|-----|---------|-------------------|-------------------------------|
| `build` | none | default | yes |
| `acceptance tests` | none | contents: read | yes (acceptance tests only), no cache |
| `acceptance` | `CLAUDE_CODE_OAUTH_TOKEN` (only in the judging step) | pull-requests: write, actions: write | no (reads files as text) |
| `review` | `CLAUDE_CODE_OAUTH_TOKEN` | pull-requests: write, actions: write | no |
| agent `work` | `CLAUDE_CODE_OAUTH_TOKEN` | contents, pull-requests, issues: write | yes (builds and tests) |
| refine | `CLAUDE_CODE_OAUTH_TOKEN` | contents: read, issues: write | no |
| `deploy` | `FLY_API_TOKEN` | default | only the built jar |
| `preview` | `FLY_ORG_TOKEN`, `APP_LOGIN_NAME`, `APP_LOGIN_PASSWORD`, `APP_GITHUB_TOKEN` | pull-requests: write | only the built jar |
| preview cleanup | `FLY_ORG_TOKEN` | default | no |

The app itself holds one fine-grained GitHub token (`GITHUB_TOKEN` env) in production and in every
preview, for ideas, the idea chat, feedback and merge.

## Scenario tags

In the proposal and in the approved delta, the tags describe a scenario against `main`.

| Tag | Meaning | Test |
|-----|---------|------|
| none | New scenario | One new test, steps word for word |
| `@ersetzt("<old title>")` | Changed scenario | The old test is replaced; the old version is shown in the idea chat |
| `@entfällt` | Removed scenario (title only) | The test is gone |
| `@manuell("<reason>")` | No automated test; the demo tour covers it | Test marked `@Pending`, annotation `@Manuell("<reason>")`; pending stage methods stay empty; `acceptance` fails a pending test without this tag and skips pending scenarios in the judging |

## Status shown in the app

| Status | When |
|--------|------|
| Refine agent works / question to the human | `refine` set, and a `refine.yml` run is / is not active |
| Coding agent works | `agent.yml` run active for the issue or its pull request |
| Review running | A required check is pending |
| Ready for demo | `build`, `e2e`, `review`, `acceptance` green |
| Waiting for the human | Otherwise (failed check, failed run, round limit) |
