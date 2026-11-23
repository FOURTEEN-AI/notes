# CLAUDE.md

A notes app, built step by step by AI agents. The current step and the rules for growing the
project are in [docs/plan.md](docs/plan.md). Read it before you start.

- Stack: Spring Boot 4 (Java 25, Gradle) serves a React app (Vite, pnpm) from `frontend/`.
  PostgreSQL. Fly.io.
- Build and test: `./gradlew build` (needs Docker for the Testcontainers database).
- Run locally: `./gradlew bootTestRun` (port 8080, login dev/dev), and for frontend work also
  `pnpm dev` in `frontend/` (port 5173, proxies `/api`).
- Deploy: every push to `main` is built, tested and deployed by `.github/workflows/ci.yml`.
- Backend in onion rings under `de.fourteen.notes`; dependencies point inwards only, and
  `OnionArchitectureTest` checks it:
  - `domain`: models, rules and ports (`NoteRepository`, `GitHub`), plain Java only. Failures are
    a `DomainException` with a kind.
  - `application`: one service per use case area, validates input and works only on the ports.
  - `infrastructure`: implements the ports (JDBC, GitHub REST) and the configuration around them.
  - `web`: thin controllers, request/response records, security, and the mapping of a
    `DomainException` kind to an HTTP status.
- Add only what the current step needs. No tool, layer or abstraction "for later".
- English for code, docs and commit messages. Exception: the JGiven acceptance tests in
  `src/test/java/.../acceptance` (test names, stage methods) are German like the scenarios they
  implement. Conventional Commits. Commit directly to `main` in a
  local session (admins are exempt from the branch protection). Agents in GitHub Actions never
  push to `main`: they work on a branch `agent/issue-<n>` and open a pull request.
- GitHub org FOURTEEN-AI: use `GH_TOKEN="$GH_TOKEN_FOURTEEN_AI" gh …`. Never print the token.

## Demo tour

Every pull request description of the coding agent contains a guided demo for its preview app,
which the preview bar shows step by step. It is a fenced block with the info string `demo` and a
JSON list of steps, in the order a human tries the change:

````
```demo
[
  {"do": "Write a note and save it", "expect": "It appears at the top of the list", "selector": "main form textarea"},
  {"do": "Click the trash icon of the note", "expect": "The note disappears", "selector": "main li .icon-button"},
  {"do": "Reload the page", "expect": "The note is still gone"}
]
```
````

- `do`: what the human does, in German, like the app. `expect`: what they should see then.
- `selector` (optional): a CSS selector for the element the step is about, taken from the code as
  it is after the change. It may match an element that appears only after an earlier step. A
  click on it moves the tour on; leave it out for steps without an element.
- Keep the block up to date with every later commit on the pull request.

## Skills

Skills live in `.claude/skills/`. Add a skill here in the same change that creates it.

- `grill-me`: interviews the user about a plan or design until shared understanding is reached.
