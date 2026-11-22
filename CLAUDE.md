# CLAUDE.md

A notes app, built step by step by AI agents. The current step and the rules for growing the
project are in [docs/plan.md](docs/plan.md). Read it before you start.

- Stack: Spring Boot 4 (Java 25, Gradle) serves a React app (Vite, pnpm) from `frontend/`.
  PostgreSQL. Fly.io.
- Build and test: `./gradlew build` (needs Docker for the Testcontainers database).
- Run locally: `./gradlew bootTestRun` (port 8080, login dev/dev), and for frontend work also
  `pnpm dev` in `frontend/` (port 5173, proxies `/api`).
- Deploy: every push to `main` is built, tested and deployed by `.github/workflows/ci.yml`.
- Add only what the current step needs. No tool, layer or abstraction "for later".
- English for code, docs and commit messages. Conventional Commits. Commit directly to `main`.
- GitHub org FOURTEEN-AI: use `GH_TOKEN="$GH_TOKEN_FOURTEEN_AI" gh …`. Never print the token.

## Skills

Skills live in `.claude/skills/`. Add a skill here in the same change that creates it.

- `grill-me`: interviews the user about a plan or design until shared understanding is reached.
