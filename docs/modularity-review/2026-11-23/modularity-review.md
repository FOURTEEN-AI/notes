# Modularity Review

**Scope**: Entire codebase: backend `src/main/java` (Spring Boot, onion rings), frontend `frontend/src` (React), and the agent workflows in `.github/` (`workflows/*.yml`, `acceptance/*.mjs`) as far as they couple to the app.
**Date**: 2026-11-23

## Executive Summary

The project is a notes app (write, list, delete notes) that has grown a second, much larger purpose: a self-hosting software loop in which the human submits ideas in the app, a refine agent and a coding agent work on GitHub issues and pull requests, and the preview apps let the human demo, give feedback and merge. Inside the Spring Boot jar the [modularity](https://coupling.dev/posts/core-concepts/modularity/) is healthy: the onion rings are enforced by a test, the `GitHub` and `ScenarioReport` ports hide the REST client, and the notes code is tiny and cohesive. The status is "needs attention", and the attention belongs at a boundary that no compiler or architecture test sees. The app and the workflows (YAML, `jq`, Node scripts) cooperate through a string protocol (comment markers, labels, run names, check names, branch and app names, the Gherkin tag syntax) that is duplicated in at least four places and two languages. That is implicit [functional coupling](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) in the [core subdomain](https://coupling.dev/posts/dimensions-of-coupling/volatility/) of the product (the agentic process changes with nearly every step), so it is the most important finding.

## Coupling Overview Table

| Integration | [Strength](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | [Distance](https://coupling.dev/posts/dimensions-of-coupling/distance/) | [Volatility](https://coupling.dev/posts/dimensions-of-coupling/volatility/) | [Balanced?](https://coupling.dev/posts/core-concepts/balance/) |
| ----------- | ----------------------------------------------------------------------------------- | ----------------------------------------------------------------------- | --------------------------------------------------------------------------- | -------------------------------------------------------------- |
| App (domain, `GitHubClient`) <-> workflows (comment markers, labels, run names, check names, branch/app names) | [Functional](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/), implicit, duplicated strings | High (other language, other runtime, GitHub-hosted, no shared type) | High (core: the process changes every step) | No (Issue 1) |
| Gherkin and proposal rules: `AcceptanceCriteria.java` <-> `gherkin.mjs` / `criteria.mjs` <-> `Ideas.tsx` | [Functional](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/), duplicated algorithm | High (3 languages, 2 runtimes) | High (core) | No (Issue 2) |
| Coding agent (prompt, CLAUDE.md) -> `PreviewService` / `Tour.tsx` (demo block, selectors) | [Functional](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) / [Model](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/), implicit, prose contract | Medium-high (agent output parsed at runtime) | Medium-high (UI changes every step) | No (Issue 3) |
| `JGivenReport` -> JGiven HTML report data in the jar | [Intrusive](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) (private file format) | Medium (build output consumed by the running app) | Low (pinned library, generic) | Tolerable (Issue 4) |
| Notes <-> Ideas/Preview in one app (`App.tsx`, one `web`/`application` package set) | Low | Low | Notes low, Ideas high | Low cohesion, minor (Issue 5) |
| Controllers <-> application records <-> TypeScript types (`PreviewResponse` = `Preview`, hand-written `Idea`, `Preview`) | [Contract](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) (JSON), hand-copied | Medium | Medium-high | Yes, with copy cost (Issue 5) |
| `IdeaService` / `PreviewService` -> `GitHub` port | [Contract](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) (port), but port is workflow-shaped | High (external service, synchronous) | Low for GitHub itself, high for what is asked of it | Yes (Issue 6, minor) |
| `NoteService` -> `NoteRepository` -> `JdbcNoteRepository` | [Contract](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low | Low (supporting) | Yes |
| `web` -> `application` -> `domain` (onion rings, `OnionArchitectureTest`) | [Contract](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) / [Model](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) | Low | Medium | Yes (cohesion) |

### How the domain was classified

- **Core subdomain** (the part that is the actual competitive edge; the "interesting problems"): the agentic idea-to-merge loop: idea chat, acceptance criteria, phases, review, demo tour, merge guard. It is the stated purpose of `docs/plan.md` and grows in every step, so it has the highest [volatility](https://coupling.dev/posts/dimensions-of-coupling/volatility/). Confidence: high.
- **Supporting subdomain** (needed, but no differentiator, simple CRUD): the notes (`Note`, `NoteService`, `JdbcNoteRepository`, the list/save/delete UI). Low volatility; pragmatic shortcuts are fine. Confidence: high.
- **Generic subdomain** (solved problem, off-the-shelf): GitHub as backlog and CI, Spring Security HTTP Basic, Fly.io, PostgreSQL, JGiven. Functional volatility is low; implementation volatility is low too (GitHub is declared the source of truth and switching it is unlikely), so heavy encapsulation of them is not needed. Confidence: medium.

### Distance and organisation

One human (product owner) plus AI agents, no teams, one deployable, one repository. There is no socio-technical distance between people, but AI agents work with a fresh context each run and read only what is written down, which makes implicit knowledge costlier than it would be for a person who remembers. Within the jar, the highest distance at the module level is the ring or package boundary (low). The workflows are the largest distance: another language, another runtime, run on GitHub's servers, and tested separately (Node tests in `.github/acceptance`, no shared types with Java).

## Issue: The workflow protocol is a set of duplicated strings shared between the app and the workflows

**Integration**: Java app (`GitHubClient`, `Issue`, `PullRequest`, `ChatMessage`, `AcceptanceCriteria`, `PreviewService`, `Checks`) <-> GitHub workflows (`agent.yml`, `refine.yml`, `acceptance.yml`, `review.yml`, `ci.yml`, `preview-cleanup.yml`, `.github/acceptance/*.mjs`) and the frontend
**Severity**: Critical

### Knowledge Leakage

The app and the workflows are two halves of one process, and they agree through conventions that exist only as string literals, never as a shared definition. Reading the code, the same knowledge is spelled out several times:

- Comment markers: `## Idea chat` (`ChatMessage.HUMAN_MARKER`, `refine.yml`, `AcceptanceCriteria.pendingProposal`), `## Refine` (`ChatMessage`, `IdeaService.previousScenarios`, `AcceptanceCriteria`, `refine.yml`, `agent.yml`), `## Acceptance criteria` (`AcceptanceCriteria.MARKER`, `criteria.mjs`, `acceptance.yml`, `agent.yml`), `## Human feedback` (`PreviewService.FEEDBACK_MARKER`, `agent.yml`), `## Acceptance checked/reset/round`, `## Review round` (only in YAML/jq, and counted by regexes in `ci.yml`, `review.yml`, `agent.yml`, `acceptance.yml`).
- Labels `refine` and `agent` (`GitHubClient.createIssue/release/openIdeaIssues`, the workflow triggers).
- Run names `Agent #<n>` and `Refine #<n>` (`run-name` in the YAML, regexes `AGENT_RUN`/`REFINE_RUN` in `GitHubClient`): the app reads a workflow's display title as if it were an API. This is close to [intrusive coupling](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/): it relies on a detail the workflow author considers cosmetic.
- Check names `build`, `review`, `acceptance`, `preview` (`GitHubClient.checks`, `Checks`, job names in YAML, branch protection).
- Branch `agent/issue-<n>` (`Issue.branch`, `PullRequest.AGENT_BRANCH`, `ci.yml`, `acceptance.yml`, `agent.yml`) and the preview app name `notes-fourteen-it-issue-<n>` (`Issue.PREVIEW_URL`, `ci.yml`, `preview-cleanup.yml`); the production URL again in `PreviewBar.tsx`.
- Environment contract `PULL_REQUEST` / `PULL_REQUEST_SHA` (`ci.yml` sets, `PreviewService` reads).
- Who counts as a human: `OWNER/MEMBER/COLLABORATOR` in `criteria.mjs` and YAML versus "any comment" in the Java code.

`docs/process.md` documents these, which is good, but a document is not a contract: nothing fails when a name drifts. The marker strings are also embedded in agent prompts, so the knowledge is shared with a third party (the LLM) that reproduces it from prose.

### Complexity Impact

A developer (or agent) changing one marker has to find every occurrence by grep across Java, YAML, `jq` expressions, JavaScript and prompts, and cannot know when it is done: there are more than 4 +/- 1 places to keep in mind, in 4 notations. The outcome of a change is only visible in hindsight (a workflow silently not triggering, an idea stuck in "Wartet auf dich", a status badge that never turns green). That is the definition of [complexity](https://coupling.dev/posts/core-concepts/complexity/). The failure mode is also quiet: `GitHubClient.checkState` returns `PENDING` for a missing check name, so a renamed check shows "läuft" forever instead of failing loudly.

### Cascading Changes

- Renaming a check or adding a required one (the plan already added `acceptance` after `build`/`review`) requires `Checks` (and its `green()`/`pending()`), `GitHubClient.checks`, `PreviewResponse`, `PreviewBar.tsx`, the frontend types, `process.md`, branch protection, and the YAML job names, all at once.
- Adding a new label or phase (e.g. discarding an idea, listed as "not in this step" in the plan) touches `GitHubClient`, `IdeaStatus`, `Idea`, the three frontend types/labels and at least two workflows.
- Changing a workflow `run-name` breaks the "agent working" status without any test failing.
- The steps in the plan show this is the hot path: the loop was reworked in steps 3, 4 and later (refine agent, phases, acceptance), each time touching both sides.

With high strength (shared functional knowledge, implicit), high [distance](https://coupling.dev/posts/dimensions-of-coupling/distance/) (other language and runtime, no compiler or architecture test in between) and the highest volatility in the system, this is the textbook unbalanced case of the [balance rule](https://coupling.dev/posts/core-concepts/balance/).

### Recommended Improvement

Do not separate further; rather make the shared knowledge explicit and singular, and reduce distance where cheap.

1. Introduce one machine-readable definition of the protocol in the repo (for example `.github/protocol.json` with markers, labels, check names, run-name prefixes, branch pattern, app-name pattern) that both sides read: the YAML/`.mjs` side at run time, the Java side through a small `infrastructure` properties class (the domain keeps its constants by receiving them through the port, so the onion stays intact). This turns implicit [functional coupling](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) into explicit [contract coupling](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/).
2. Where a file is not feasible, add a drift test (Java or Node) that parses the workflows and asserts that every marker, label, check name and run-name pattern used by the Java side appears in them. It is cheap and fails loudly instead of quietly.
3. Replace the `display_title` regex with something the workflow owns on purpose (a dedicated check name or a label on the run), or at least cover it with the drift test.
4. Make a missing check an explicit `MISSING` state instead of `PENDING`.

Trade-offs: one more artefact to maintain, and a small indirection when reading the Java constants. Worth it because this is the area the product exists to change, and AI agents (which do not remember conventions between runs) benefit most from a single, checkable source.

## Issue: Gherkin and "pending proposal" rules are implemented in Java, Node and TypeScript

**Integration**: `domain/AcceptanceCriteria.java` <-> `.github/acceptance/gherkin.mjs`, `criteria.mjs`, `conflict.mjs` <-> `frontend/src/Ideas.tsx`
**Severity**: Significant

### Knowledge Leakage

The syntax of the approved delta (German `Szenario:`/`Szenariogrundriss:` headers, tags `@ersetzt("<old title>")`, `@entfällt`, `@manuell`) is parsed twice: `AcceptanceCriteria.oldTitles` in Java (to show the previous scenarios in the idea chat) and `parseGherkin` in `gherkin.mjs` (to compare tests with the delta). Both are independent regex implementations of one language: duplicated algorithmic knowledge, the strongest form of [functional coupling](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/). The "proposal block" rule is a third copy: the first gherkin code fence fence (`AcceptanceCriteria.BLOCK`, with its tolerance for an unclosed block) is re-implemented in `Ideas.tsx` as a `text.includes(...)` check for the gherkin fence, which decides whether the "Freigeben" button appears and whether previous scenarios are fetched. The backend (`pendingProposal`) is the real authority; the frontend only guesses, and the two can disagree (for example a fence that is blank, or a message that is not the pending one).

### Complexity Impact

A syntax extension (a new tag, English keywords, `Hintergrund`) must be made in at least two parsers with different regex flavours and different test suites (`AcceptanceCriteriaTest`, `criteria.test.mjs`/`stages.test.mjs`). Nobody can tell from either file that the other exists, so a divergence shows up only as a confusing symptom, such as the chat showing the wrong "Bisher" scenarios while `acceptance` judges against others.

### Cascading Changes

Any change to the tag language (the plan already evolved it through `@ersetzt`, `@entfällt`, `@manuell`) forces a coordinated edit in Java and Node (and the refine agent prompt that teaches the syntax). A change to what counts as a pending proposal forces a change in `AcceptanceCriteria.pendingProposal`, the preview bar's `proposal` field and the frontend heuristic. Distance is high (different runtimes, no shared code), volatility high (core), so the imbalance is real, but it is smaller in scope than Issue 1.

### Recommended Improvement

- Let the backend say it. Add a `canApprove` (or `proposal`) flag to the idea chat response, derived from `pendingProposal`, and delete the `includes` heuristic from `Ideas.tsx`. That removes one copy and lowers strength to contract coupling.
- For Java vs Node, pick one owner of the tag syntax. The Java side only needs old titles; either let the workflow publish them (the acceptance job already parses the delta and could post the replaced titles) or add a shared fixture file of Gherkin examples with expected results, run by both test suites. A shared fixture is the smaller step and keeps both parsers honest.
- Do not merge the Java and Node runtimes just to share code.

Trade-off: an extra field in the API and a shared fixture to maintain, in exchange for one definition of "what the human can approve" and a test that fails on drift.

## Issue: The demo tour is an unwritten contract between the coding agent, the Java parser and the page markup

**Integration**: Coding agent (prompt in `agent.yml`, rule in `CLAUDE.md`) -> `PreviewService.demo` / `DemoStep` -> `PreviewController` -> `Tour.tsx` (selectors against the live DOM)
**Severity**: Significant

### Knowledge Leakage

The format of the demo code fence block (JSON list with `do`, `expect`, `selector`) is defined in prose in `CLAUDE.md`, restated in the agent prompt, parsed by `PreviewService` (a private `WrittenStep` record, the regex `DEMO_BLOCK`), copied to `DemoStep` and `DemoStepResponse` (with `@JsonProperty("do")` repeated), and typed again in `Tour.tsx`. More importantly, `selector` is a CSS selector written by an agent "from the code as it is after the change": the tour is coupled to the rendered markup and class names of the frontend (`main form textarea`, `main li .icon-button`) without any contract. Invalid input is swallowed on purpose (`catch (JacksonException) -> List.of()`; unmatched selectors highlight nothing), so breakage is silent.

### Complexity Impact

A frontend refactoring (renaming a class, moving a form) cannot be recognised as breaking demos: no test, type or build step relates selectors to markup. A malformed block gives an empty tour, which looks the same as "this change has no demo". The developer has to remember a rule that lives in a prose file and a prompt. Also, `PreviewService` in the `application` ring imports Jackson annotations and does text parsing for a format that is really an integration contract with an LLM: a small leak of a delivery detail into the inner rings.

### Cascading Changes

Each UI step changes markup (this is the most volatile part of the frontend: every feature touches it), so every pending pull request's selectors can go stale after a merge to `main` or after human feedback. Adding a field to a demo step (for example a `url` or `screenshot`) needs edits in CLAUDE.md, the prompt, `WrittenStep`, `DemoStep`, `DemoStepResponse`, `PreviewResponse` and `Tour.tsx`. Since the demo is the human's only look at the change (the plan: "the human sees no code, only the demo"), a quiet failure here removes the safety net for core functionality.

### Recommended Improvement

- Move the parse of the demo block out of `application` into a small adapter (the same place the `GitHub` port lives), so that Jackson and the fence format stay outside the domain rules; the application only handles `DemoStep`.
- Make failures visible: when a demo code fence block exists but is invalid, return a flag or a message ("Demo konnte nicht gelesen werden") and show it in the bar; make the tour mark a step "Element nicht gefunden".
- Add one test that renders the page and checks the selectors of the demo block of a sample pull request description; or have the review agent verify selectors against the markup (the review already reads the diff).
- Prefer stable hooks (`data-demo="..."` attributes) over structural selectors, and say so in CLAUDE.md. This trades a little markup noise for a contract that survives styling changes.

Trade-off: a few attributes in the markup and one more state in the bar; the gain is that the highest-leverage human checkpoint stops failing silently.

## Issue: `JGivenReport` reads the private file format of a test report

**Integration**: `infrastructure/JGivenReport` -> JGiven HTML report data in the jar (`static/jgiven/data/*.js`)
**Severity**: Minor

### Knowledge Leakage

The adapter extracts scenarios through `addZippedScenarios('<base64 of gzipped JSON>')` and walks `scenarios[].scenarios[].scenarioCases[0].steps[].words[]`, including `depth`, `argumentInfo`, and the German keywords map. That is JGiven's undocumented report layout, [intrusive coupling](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) on a build artefact. The knowledge of how the report gets into the jar sits in the build files, the notion that its scenarios equal `main`'s scenarios in `ScenarioReport`'s Javadoc.

### Complexity Impact

A JGiven upgrade could change the format and the effect would only be a missing "Bisher" panel (and a `UPSTREAM_FAILED` for an unreadable file). Because the report only exists in a real build, the unit and local runs ("Without a report (tests, bootTestRun) there is nothing to show") do not exercise the parser at all, so a break is first seen in production.

### Cascading Changes

Only a library upgrade or a change of the report mechanism. JGiven is pinned and generic, so volatility is low, and the blast radius is a convenience feature of the idea chat. Unbalanced but tolerable.

### Recommended Improvement

Accept it, but add one parser test against a small captured fixture of a real report file so the upgrade breaks the build rather than the chat. Optionally, have the Gradle build export a plain `scenarios.json` (via a JGiven JSON report or a tiny task) so that the adapter reads a format the project owns. Trade-off: a fixture to refresh on upgrades.

## Issue: Notes and the idea/preview machinery share one app, one frontend entry and hand-copied DTO layers

**Integration**: `NoteController`/`NoteService` and `App.tsx` <-> `IdeaController`/`IdeaService`/`PreviewController`/`PreviewService`, `Ideas.tsx`, `PreviewBar.tsx`
**Severity**: Minor

### Knowledge Leakage

The two features share no knowledge (the only common code is `DomainException` and the security config), which is [low strength](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/) at low [distance](https://coupling.dev/posts/dimensions-of-coupling/distance/): a mild case of [low cohesion](https://coupling.dev/posts/core-concepts/balance/). Layers are organised by technical ring, not by subdomain, so `application/` and `web/` mix a supporting subdomain (notes) with the volatile core (ideas, preview). In the frontend, `Ideas.tsx` (303 lines) decides whether it runs in production or a preview by calling `/api/preview` and checking for 200 (a hidden mode switch), and `PreviewBar` hard-codes the production URL. Mode (production versus preview) is a deployment fact that leaks into two components and into the backend's `isPreview()` semantics (204 instead of 404 to keep the browser console quiet).

The DTO layers are copied by hand: `PreviewService.Preview` and `PreviewController.PreviewResponse` have identical components; `Idea`, `ChatMessage`, `Scenario` records are serialised directly while the TypeScript types are written by hand. This is correct [contract coupling](https://coupling.dev/posts/dimensions-of-coupling/integration-strength/), only without a generator or a contract test, so drift is possible (for example a new `IdeaStatus` value unknown to `statusLabels`).

### Complexity Impact

Small today (about 1,000 lines of production code in total). The cost appears when the core grows: every step adds to `Ideas.tsx` and the `IdeaService`, in the same package and files that hold the note logic, and the 4 +/- 1 limit is reached sooner in a 300-line component with polling, dialog, chat, approval and list state.

### Cascading Changes

Adding a field to the preview needs three edits (`Preview`, `PreviewResponse`, TS type). Adding a status needs `IdeaStatus`, `statusLabels`, CSS classes. These are cheap because distance is low, which is why this stays Minor.

### Recommended Improvement

Not a split into services (that would raise distance for no gain). Cheaper moves, when the next step touches the area anyway: split `Ideas.tsx` into list, chat and `useIdeas` hook files; group the frontend by feature folders (`notes/`, `ideas/`, `preview/`); expose the mode (`production` or `preview`) once from a single `/api/mode` or config endpoint instead of probing; and let the controller return the application record (or a mapping with a test) instead of a field-for-field copy. Do this opportunistically.

## Issue: The `GitHub` port is shaped like the workflow, and lists are assembled with synchronous per-item calls

**Integration**: `IdeaService`, `PreviewService` -> `domain.GitHub` -> `GitHubClient`
**Severity**: Minor

### Knowledge Leakage

The port is a good boundary: the REST client and JSON are hidden, and the error kinds are mapped. But the port is named and shaped after this particular process: `release` means "swap label `refine` for `agent`", `activeAgentRuns` means "runs of `agent.yml` whose title matches", `openIdeaIssues` means "labelled `refine` or `agent`", `checks` means "the four fixed checks". Workflow knowledge (Issue 1) therefore sits partly in the domain (which was meant to be plain business rules) and partly in the adapter, with the split between them following no clear rule. For a generic subdomain with low switching probability (GitHub is declared the source of truth), that is acceptable.

### Complexity Impact

`IdeaService.list` calls GitHub 4 times plus one `checks` call per idea, and the frontend polls every 10 seconds while the dialog is open (and the preview bar every 10 seconds per open preview): the runtime coupling is synchronous and chatty, bounded by the GitHub rate limit of the single shared token. With one user it is fine; it is a latent scaling and failure-cascade point, not a design flaw at this size.

### Cascading Changes

Moving workflow semantics (a new label, a second workflow) means changing a domain interface and its implementation together. Since both are in the same module and the same team (human plus agents), that is cheap.

### Recommended Improvement

Keep the port. When Issue 1 is addressed, let the protocol constants live next to the adapter, and keep the port to intent-level verbs (`submitIdea`, `releaseIdea`, `statusOf`). If rate limits or latency show up, fetch the statuses on the server on a timer and serve the cached result. No action needed before that.

---

_This analysis was performed using the [Balanced Coupling](https://coupling.dev) model by [Vlad Khononov](https://vladikk.com)._
