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
| 9 | Human | Walks the tour. Feedback, or merge | Feedback: comment `## Human feedback`, starts the agent. Merge: squash, only with green `build`, `review`, `acceptance` |
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
| `review` | `review.yml` | yes | yes | Fails without a review until phase 2 has pushed on top of the checked commit |
| `acceptance` | `acceptance.yml` `acceptance` | yes | yes | Passes for branches other than `agent/issue-<n>` |
| `preview` | `ci.yml` `preview` | no | no | Only the deploy; the preview link shows once it is green. Skipped in phase 1 (no jar) |

`build` also checks formatting (`check`): Spotless (google-java-format AOSP style, 4 spaces, unused imports removed, imports sorted) for Java and Prettier (`frontend/.prettierrc.json`) for `frontend/src` and its config files. `./gradlew format` fixes both (`pnpm run format` only the frontend).

`build` also compiles strictly: javac with `-Xlint:all -Werror` (tests without `deprecation`: JGiven's `ScenarioTest`), Error Prone (bug patterns, warnings are errors) and NullAway on main sources. Every package of `de.fourteen.notes` is `@NullMarked` (JSpecify, `package-info.java`); where null is allowed it says `@Nullable`. `OnionArchitectureTest` lets `domain` import `org.jspecify` besides the JDK.

Branch protection on `main`: pull request, the three required checks, admins exempt. The merge
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
| Ready for demo | `build`, `review`, `acceptance` green |
| Waiting for the human | Otherwise (failed check, failed run, round limit) |
