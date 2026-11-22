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

## Step 1: write and read notes, online (current)

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

- [ ] Fly app, Postgres, secrets and `FLY_API_TOKEN` are set up (see README)
- [ ] A push to `main` deploys without manual steps
- [ ] A note written on the phone shows up on the laptop
- [ ] The note is still there after the next deploy

## Candidates for later steps

Not planned, only to keep ideas. The order is decided when a step is done.

- Edit and delete notes.
- Linting and formatting, once style discussions start to cost time.
- Pull requests with branch protection, once changes should be reviewed before they deploy.
- Database migrations (e.g. Flyway), with the first schema change that `create table if not
  exists` cannot express.
- An agent that takes a GitHub issue to a finished change on its own.
