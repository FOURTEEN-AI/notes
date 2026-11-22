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

## Step 2: an agent takes an issue to a pull request (current)

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
- Branch protection on `main`: pull request, green CI and green review required. Direct pushes
  are no longer possible.
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

- [ ] Branch protection on `main` requires pull request, green CI and green review
- [ ] A labelled issue produces a pull request without manual steps
- [ ] The review agent blocks a pull request with a finding and the coding agent fixes it
- [ ] Every pull request has a preview app, removed when the pull request closes
- [ ] "Delete notes" is merged after a demo and live in production

## Candidates for later steps

Not planned, only to keep ideas. The order is decided when a step is done.

- Strengthen the harness: what the agents need to work more reliably (feedback such as checks
  and tests, instructions, guardrails, observability), based on what the run with issue #1 shows.
- Edit notes.
- Linting and formatting, once style discussions start to cost time.
- Database migrations (e.g. Flyway), with the first schema change that `create table if not
  exists` cannot express.
