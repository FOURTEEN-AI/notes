# Plan

## Idea

A notes app, built with an agentic software development lifecycle: AI agents do the work, a human
reviews, decides and demos. App and process both start as simple as possible and grow in small
steps.

"A complex system that works is invariably found to have evolved from a simple system that
worked." (Gall's law)

## Rules for growing

- Every step ends with something that runs in production and can be shown.
- Something is added only when the current step needs it: a tool, a layer, a test type, a
  process step. No groundwork "for later".
- A decision is written down only once it was made and its reasons are worth remembering. Until
  then this file is enough.
- The next step is chosen after the current one is done, based on what was learned.

## Step 1: write and read notes, online (done)

**Result:** a URL on Fly.io behind a login. A text field with a save button, below it all notes,
newest first. Notes survive restarts and deploys.

**Scope**

- A note is text plus a timestamp. No title, no editing, no deleting.
- Spring Boot serves a REST API (`GET`/`POST /api/notes`) and the built React app.
- PostgreSQL as unmanaged Fly Postgres. The schema is one `schema.sql` with
  `create table if not exists`, applied on start.
- One user with HTTP Basic, from environment variables.
- Tests: an API test against PostgreSQL (Testcontainers) and a unit test for the `DATABASE_URL`
  mapping.
- CI on GitHub Actions: build and test on every push and pull request; every push to `main` is
  deployed to Fly.io.
- A devcontainer with Java, Node, pnpm, Docker, GitHub CLI, flyctl and Claude Code.

**Process:** the human describes the task, an agent implements and tests it, the human reviews.
Commits go directly to `main`; CI deploys them.

**Not in this step:** editing and deleting, multiple users, migrations tool, linting and
formatting, branch protection and pull requests, preview environments, observability, a backlog
tool, ADRs.

**Done when**

- [x] Fly app, Postgres, secrets and `FLY_API_TOKEN` are set up (see README)
- [x] A push to `main` deploys without manual steps
- [x] A note written on the phone shows up on the laptop
- [x] The note is still there after the next deploy

## Step 2: an agent takes an issue to a pull request (done)

**Result:** a GitHub issue labelled `agent` becomes a pull request with a preview app. A second
agent reviews it. The human sees no code, only the demo, and merges. Proven with the issue
"delete notes".

**Scope**

- Coding agent: a GitHub Action (Claude Code, paid by the Claude subscription via
  `CLAUDE_CODE_OAUTH_TOKEN`) starts when the label `agent` is set on an issue and opens a pull
  request. It has no `workflows` permission and cannot change workflow files.
- Review agent: its own workflow with fresh context and its own instructions. It also checks that
  the change brings tests that cover its behaviour. It is a required check.
- A failed review goes back to the coding agent as a comment; the agent pushes a fix and the
  review runs again, at most 3 rounds, then the pull request waits for the human.
- Branch protection on `main`: pull request, green CI and green review required. Admins are
  exempt, so a local session can still commit directly to `main`; agents and everyone else go
  through a pull request.
- Preview app per pull request on Fly (`notes-fourteen-it-issue-<n>`, named after the issue), with its own schema in the
  existing Postgres and the same login as production. Removed when the pull request is closed.
- Build and test run without secrets. Only a separate deploy job gets the Fly org token
  (second secret, next to the production deploy token) and only handles the built jar.
- The first task is the issue "delete notes": endpoint, button and test.

**Process:** the human writes an issue and sets the label, watches the preview, merges. Agents
write the code and review it.

**Not in this step:** editing notes, linting and formatting, a migrations tool.

**Risks:** without human code review, quality rests on tests and the review agent; a missed
mistake shows only if the demo reveals it. The review loop can use up much of the subscription
limit, hence the 3 rounds.

**Done when**

- [x] Branch protection on `main` requires pull request, green CI and green review
- [x] A labelled issue produces a pull request without manual steps
- [x] The review agent blocks a pull request with a finding and the coding agent fixes it
- [x] Every pull request has a preview app, removed when the pull request closes
- [x] "Delete notes" is merged after a demo and live in production

## Step 3: from idea to merge without GitHub (done)

**Result:** the human runs the whole loop from the app. A feature idea is submitted in production,
its progress is visible there, and the new feature is demoed, commented on and merged in its
preview app. GitHub is no longer opened.

**Scope**

- GitHub stays the source of truth (issues, pull requests, labels). The app talks to the GitHub
  API; the agent and review workflows keep working as in step 2.
- Production: a form with title and description creates an issue with the label `agent`, so the
  coding agent starts right away. Below it, a list of the open issues labelled `agent`, each with
  a status derived from its pull request and checks (agent working, review running, ready for
  demo, waiting for the human) and a link to its preview app. A merged pull request closes its
  issue, which then leaves the list.
- Preview app: a bar on top with the pull request's title, the state of its checks, a comment
  field and a merge button for exactly this pull request. The deploy job passes the pull request
  number as an environment variable; without it the bar is not shown.
- Feedback: the comment is posted on the pull request and starts with `## Human feedback`.
  `agent.yml` gets an `issue_comment` trigger for this marker, and the coding agent implements
  exactly that feedback; the review then runs again. Human feedback has no round limit. The same
  comment written directly on GitHub works too.
- Merge: only when the checks `build` and `review` are green. The app checks this itself,
  because the token belongs to an admin and could bypass the branch protection.
- Access: a fine-grained personal access token for `FOURTEEN-AI/notes` only (Issues, Pull
  requests, Contents: write). A Fly secret in production, and a GitHub Actions secret that the
  preview deploy job passes to the preview apps.
- Tests: the GitHub API is faked with Spring's own test support, no new test tool.

**Process:** built in a local session and committed directly to `main`, including the workflow
changes, which the coding agent may not make.

**Not in this step:** discarding an idea from the app, notifications, a separate login for the
previews, a GitHub App or GitHub login instead of the token, ideas stored in the app's database.

**Risks:** the preview apps run code that only the review agent has read, and they hold the
token. Such code (an agent mistake, a bad dependency, a prompt injection) could read the token
and push to `main` as an admin, which CI then deploys, or merge its own pull request without a
demo. The previews also share the production login, so their code can already call the
production API. Accepted for now: only the human writes issues, so the chance is low. The fix,
if it becomes necessary, is to keep the token in production only, give the previews their own
login, and let them comment and merge through production.

**Done when**

- [x] An idea submitted in production becomes an issue and the coding agent starts
- [x] The list in production shows each idea's status and links to its preview
- [x] A comment from the preview bar makes the coding agent push a fix
- [x] The merge button is only enabled with green `build` and `review`, and merges from the
  preview
- [x] A feature went from idea to production without opening GitHub

## Step 4: a guided demo in the preview (done)

**Result:** a preview app walks the human through its change step by step. Each step says what
to do and what should happen, and highlights the element on the page. The human no longer has to
read the pull request description to know what to try.

**Scope**

- The coding agent writes the tour as a ` ```demo ` block in the pull request description: a JSON
  list of steps with `do`, `expect` and an optional CSS `selector` (format in CLAUDE.md). It keeps
  the block up to date when it fixes review findings or implements human feedback. Nothing of the
  tour lands on `main`.
- `/api/preview` returns the steps parsed from the description. A missing or broken block means
  no tour.
- The tour starts by itself on the first visit of a commit and survives reloads (browser storage,
  per deployed commit); a newer commit starts it from step 1. It moves on with "Weiter" or by
  clicking the highlighted element. An element that appears later is highlighted as soon as it
  exists; a step whose selector matches nothing is shown as text only.
- "Passt nicht" on a step fills the feedback field with the step and its expectation.
- The review fails a pull request whose block is missing, is not valid JSON, or has a selector
  that clearly does not match the code.
- Built in-house in the frontend, no tour library.

**Process:** built in a local session and committed directly to `main`, including the workflow
changes, which the coding agent may not make.

**Not in this step:** running the tour automatically in CI (e.g. Playwright), tours in production.

**Done when**

- [x] `/api/preview` returns the steps of the ` ```demo ` block
- [x] The tour starts in a preview, highlights its elements and survives a reload
- [x] The next agent issue ("reverse the sort order", #6) arrives with a tour that the human walks through
  without reading the pull request description

## Step 5: a refine agent asks back before coding (done)

**Result:** an idea is cleared up in its chat before the coding agent starts. The refine agent asks
short questions, the human answers in the chat, and the agent releases the idea when it is clear.

**Scope**

- The app creates an idea as an issue with the label `refine` instead of `agent`. The new workflow
  `refine.yml` starts the refine agent on it and on every answer.
- The agent reads the idea, the chat, the code and the docs. It asks at most 3 short questions per
  round, in a comment that starts with `## Refine`, or releases the idea with its assumptions: it
  swaps `refine` for `agent`, which starts the coding agent as before. After 3 rounds it releases
  the idea anyway. It can only read and comment; it never changes the issue text or any file.
- Each idea has its own chat in the app: the description, then the issue comments that start with
  `## Idea chat` (the human) or `## Refine` (the agent), then the preview link once the preview is
  deployed. The status stays a status ("Refine-Agent arbeitet", "Rückfrage an dich", …), not a
  message. The input is shown while the idea carries `refine`.

**Not in this step:** a button to release an idea by hand, discarding an idea, replies after the
coding agent started (that is the preview bar's feedback).

**Risks:** the idea and the chat are untrusted text for an agent that can edit labels; it has no
write access to code, and the coding agent's own limits stay as they are.

## Step 6: approved acceptance criteria come before the code

**Result:** an idea leaves its refinement with Gherkin scenarios that the human has approved. The
coding agent first turns them into JGiven acceptance tests, a check proves that the tests match
the scenarios, and only then is the production code written. The JGiven report, served by the
app, is the living documentation.

**Scope**

- Refinement: after at most 3 rounds of questions the refine agent proposes the acceptance
  criteria as a ` ```gherkin ` block in German (`# language: de`) in its `## Refine` comment. The
  proposal is a delta against `main`, as far as the agent can see it from the acceptance tests:
  new scenarios have no tag, changed ones `@ersetzt("<old title>")`, removed ones are a title stub
  with `@entfällt`. A scenario that gets no acceptance test, because only the frontend shows it
  or for another reason, is tagged `@manuell("<reason>")`; the demo tour covers it.
- Approval: only the human approves; the release after 3 rounds goes away. The idea chat shows a
  button "Freigeben" and, for every changed or removed scenario, its old version from the report
  of `main`. A text answer starts another round instead. The button makes the app post a comment
  `## Acceptance criteria` with the copied block, then swap `refine` for `agent`. The scenarios
  never land in the repository.
- Acceptance tests in JGiven, port to port: the When step calls an application service,
  `NoteRepository` and `GitHub` are in-memory fakes, no Spring, no database. Stage methods are
  named in German like the steps, an exception to "English for code" that goes into CLAUDE.md.
  The scenario title is the test's title and its identity, unique across the report. No origin
  tag; the history is in git.
- A `@manuell` scenario still gets a JGiven test, marked `@Pending` and tagged with its reason, so
  the report shows it as pending and a change or removal has an old version to compare with, in
  the check and in the idea chat. A stage method that only such scenarios use is `@Pending` too.
  First example from the notes: the input field is empty after saving.
- The coding agent works in two phases. Phase 1 writes only the acceptance tests, stages, fakes
  and compiling stubs (new port methods included) and pushes. Phase 2 starts only when
  `acceptance` is green and writes the production code; the test classes, stages and fakes must
  stay unchanged against the checked commit. Needing a change there means back to phase 1.
- A new workflow `acceptance`, a required check and the only job with the decision model's key.
  It reads the latest `## Acceptance criteria` and the JGiven JSON report from `build`, and
  compares it with the report of the current `main`:
  - Deterministic: the scenarios added, changed and removed against `main` are exactly the
    approved delta, and every scenario has one test whose steps match word for word (keyword,
    order, text, parameters as placeholders). A `@manuell` scenario's test is pending with the
    approved reason, every other test is not pending, and no old test becomes pending outside the
    delta.
  - Per stage method: every stage and fake method that changed against `main` is judged in every
    scenario that uses it, not only in the delta. Pending scenarios are skipped; their steps do
    not run. The state is the scenario, the step text, the
    method's source and the fake and service methods it calls; the question is whether the method
    implements exactly this step. Jev (TypeSafe, own budget and key) decides: from p ≥ 0.9 it
    passes, below 0.5 it fails, in between Haiku decides (`claude -p --model claude-haiku-5-5`
    with the existing OAuth token).
  - A failure goes back to the coding agent as a comment `## Acceptance round <n>`, at most 3
    rounds, then the pull request waits for the human.
- Late conflicts: an old acceptance test that is red after phase 2, or a delta that no longer
  fits the current `main` (a parallel pull request was merged), sends the idea back to the idea
  chat: the coding agent proposes an amended delta in a `## Refine` comment and the label goes
  back to `refine`. The new `## Acceptance criteria` replaces the previous one as a full delta, and
  phase 1 starts again. Human feedback from the preview that changes approved behaviour takes the
  same path; the preview bar shows the proposal and links to the idea chat in production, where it is
  released with the old versions in view.
- The review agent runs only after phase 2. Branch protection and the merge button require
  `build`, `review` and `acceptance`.
- The app serves the JGiven HTML report behind the login, in production and in every preview.
- The existing behaviour of notes gets scenarios and JGiven tests. Existing tests stay.
- At the end of the step, the process as it is then gets a short description of its own, separate
  from the history of steps in this file: who does what when, which labels, comments and checks.
  It is updated at the end of every later step, as the JGiven report is for the behaviour of the
  app.

**Process:** built in a local session and committed directly to `main`, including the workflow
changes, which the coding agent may not make.

**Not in this step:** scenarios for ideas, automated tests for `@manuell` scenarios, rewriting
the existing tests, `.feature` files.

**Risks:** the decision model judges with a probability, so a test that is too weak can still
pass; the deterministic checks only guard the wording and the delta. A hidden conflict with an
old scenario shows only after phase 2, when phase 1, `acceptance` and phase 2 have already run.
A `@manuell` scenario is checked only for its wording; whether it holds shows only in the demo.
Jev costs its own budget, Haiku the subscription limit.

**Progress:** done so far: JGiven tests of the notes (title = `@Description`, the report shows
it), the report in the jar (`/jgiven/`), proposal and the button "Freigeben" (with the old
versions of changed scenarios), the deterministic comparison of approved delta and report
(`.github/acceptance/`, tested in `ci.yml`), the workflow `acceptance` around it (latest
`## Acceptance criteria`, tests of `main` and of the pull request, the comment
`## Acceptance round <n>`, which starts the coding agent, at most 3 rounds), `acceptance` as a
required check in branch protection and for the merge button (preview bar lists it), the judgement
per stage and fake method (Jev, Haiku in between; needs the secret `TYPESAFE_API_KEY`), the two phases of the coding agent
(`agent.yml` prompts per phase; green `acceptance` in phase 1 posts `## Acceptance checked <sha>`
and starts phase 2; `.github/acceptance/phase.mjs` fails a phase 2 that changes the acceptance
tests against that commit, a commit with the line `Phase: 1` goes back; `review` fails until phase 2
has pushed), late conflicts (`acceptance` posts `## Acceptance conflict` when a test of `main` that
the delta leaves alone is red after phase 2, or when it has findings while the branch is behind
`main`; `agent.yml` then only proposes an amended full delta in a `## Refine` comment and swaps
`agent` for `refine`; the next release posts the new `## Acceptance criteria`, which replaces the
old one, and phase 1 repeats on the same branch after a `## Acceptance reset` comment that voids the
checked commit and the rounds; the preview bar shows a pending proposal and links to its idea
chat in production (`?idee=<n>`), the only place with the report of `main`; feedback that changes approved
behaviour takes the same path), the old versions in the idea chat (`GET /api/ideas/{n}/previous-scenarios`
reads title and steps of every `@ersetzt` and `@entfällt` scenario from the JGiven data of the
running build, which is `main` in production; a preview shows its own build's report),
pending scenarios instead of `@ui` (the tag is `@manuell("<reason>")`; the test is `@Pending` with
the annotation `@Manuell("<reason>")`, `report.mjs` reads status and reason from the JGiven JSON,
`check.mjs` compares them with the delta, `stages.mjs` skips pending scenarios; first example:
"Das Eingabefeld ist nach dem Speichern leer"), and the description of the current process in [process.md](process.md).

**Done when**

- [x] The refine agent proposes scenarios, and only the button "Freigeben" releases the idea
- [ ] `acceptance` fails a test that does not implement its step, and a test removed outside the
  delta
- [ ] Phase 2 starts only after a green `acceptance` and cannot change the checked tests
- [x] The notes' behaviour has scenarios and tests, and the report is served in production
- [ ] A `@manuell` scenario is pending in the report, and `acceptance` fails a test that is pending
  without approval
- [x] The current process is described apart from the history of steps
- [ ] An agent issue went from approved scenarios to production, with at least one finding of
  `acceptance` on the way

## Candidates for later steps

Not planned, only to keep ideas. The order is decided when a step is done.

- Strengthen the harness: what the agents need to work more reliably (feedback such as checks
  and tests, instructions, guardrails, observability), based on what the run with issue #1 shows.
- Edit notes.
- Linting and formatting, once style discussions start to cost time.
- Database migrations (e.g. Flyway), with the first schema change that `create table if not
  exists` cannot express.
- "Als neue Idee" in the preview bar: feedback that does not block the merge (an idea, polish, a
  nice-to-have) becomes a new idea with the label `refine` instead of going to the coding agent,
  so the pull request does not grow. Only blocking feedback stays `## Human feedback`.
- WIP limit 1 for `agent`: "Freigeben" is refused while another idea holds the slot (label
  `agent` or an open pull request `agent/issue-<n>`); the idea that holds it may be released
  again (amended delta). The button says which idea is in the way. Fewer late conflicts and
  previews.
- Assumptions instead of stopping: where an agent stops today and waits for the human (after 3
  rounds of review or `acceptance`, an open question in refinement), it decides by default rules
  instead (follow an existing pattern, the more restrictive variant, the smallest scope) and
  writes the assumption down. Every assumption becomes a step of the demo tour ("Annahme: …"),
  where the human confirms it or answers with "Passt nicht".
- Size limits and vertical slicing: an idea with more than about 12 scenarios is too big for one
  pull request and one demo. The refine agent splits it into several ideas, each a thin slice
  through all layers that can be demoed on its own, never along layers (domain, API, UI).
