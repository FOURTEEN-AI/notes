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
- Preview app per pull request on Fly (`notes-fourteen-it-pr-<n>`), with its own schema in the
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

## Candidates for later steps

Not planned, only to keep ideas. The order is decided when a step is done.

- Strengthen the harness: what the agents need to work more reliably (feedback such as checks
  and tests, instructions, guardrails, observability), based on what the run with issue #1 shows.
- Edit notes.
- Linting and formatting, once style discussions start to cost time.
- Database migrations (e.g. Flyway), with the first schema change that `create table if not
  exists` cannot express.
