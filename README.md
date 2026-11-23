# notes

A small notes app: write a note, see all notes, newest first. It grows in small steps, see
[docs/plan.md](docs/plan.md).

## Develop

Open the repository in the devcontainer (`.devcontainer/`). Then:

```sh
./gradlew build         # build frontend and backend, run all tests
./gradlew bootTestRun   # app on http://localhost:8080 with a throwaway database, login dev/dev
cd frontend && pnpm dev # optional: frontend with hot reload on http://localhost:5173
```

## Deploy

Every push to `main` runs `.github/workflows/ci.yml`: build and test, then `flyctl deploy`.

One-time setup on Fly.io (app `notes-fourteen-it`, region `fra`):

```sh
fly apps create notes-fourteen-it

# Unmanaged Fly Postgres, a single small machine.
fly postgres create --name notes-fourteen-it-db --region fra \
  --initial-cluster-size 1 --vm-size shared-cpu-1x --volume-size 1
# 256 MB RAM is not enough once preview apps connect too.
fly machine update <db-machine-id> --app notes-fourteen-it-db --vm-memory 512
# Creates the database and user and sets the secret DATABASE_URL on the app.
fly postgres attach notes-fourteen-it-db --app notes-fourteen-it

# The one login of the app.
fly secrets set --app notes-fourteen-it --stage \
  SPRING_SECURITY_USER_NAME=<name> SPRING_SECURITY_USER_PASSWORD=<password>

# Deploy token for CI.
fly tokens create deploy --app notes-fourteen-it \
  | GH_TOKEN="$GH_TOKEN_FOURTEEN_AI" gh secret set FLY_API_TOKEN --repo FOURTEEN-AI/notes
```

The app runs on one machine (`--ha=false` in CI). The first deploy creates two; remove the
extra one with `fly machines list --app notes-fourteen-it` and `fly machines destroy <id>`.

### Custom domain

The app is served at https://notes.fourteen-it.de. At the DNS provider (IONOS) add a `CNAME`
record `notes` → `notes-fourteen-it.fly.dev`, then:

```sh
fly certs add notes.fourteen-it.de --app notes-fourteen-it
fly certs check notes.fourteen-it.de --app notes-fourteen-it
```

## Agents

A new idea is an issue with the label `refine`. `.github/workflows/refine.yml` starts the refine agent on it: it
asks back with a comment that starts with `## Refine` (at most 3 rounds), or releases the idea by
swapping `refine` for `agent`. An answer is a comment that starts with `## Idea chat` (by the
owner, a member or a collaborator) and starts it again; it can only read and comment.

A GitHub issue with the label `agent` is taken to a pull request by `.github/workflows/agent.yml`.
`.github/workflows/review.yml` reviews every pull request; its check `review` must pass. A failed
review on an `agent/*` branch goes back to the coding agent, at most 3 rounds. `ci.yml` deploys a
preview app `notes-fourteen-it-issue-<n>` per pull request, named after its issue (own database `issue_<n>` in the existing
Postgres, same login as production); `preview-cleanup.yml` removes both when the pull request is
closed. Both agents run on `claude-sonnet-5-5`, set with `--model` in `claude_args`.

A comment on a pull request that starts with `## Human feedback` (by the owner, a member or a
collaborator) starts the coding agent on that pull request again, without a round limit.

## Ideas and preview bar

The app talks to GitHub with a token, so the whole loop runs without opening GitHub:

- Production: "Ideen" creates an issue with the label `refine` and lists the open ones with `refine` or
  `agent` and their status (refine agent working, question for you, agent working, review running,
  ready for the demo, waiting for the human). A click opens the idea's chat: the description, the
  comments with the markers `## Idea chat` and `## Refine`, an input while the label is `refine`, and
  the preview link once its check `preview` (the deploy) is green. The status comes from the pull request on `agent/issue-<n>`, its checks `build`
  and `review`, and running `agent.yml` runs (their `run-name` is `Agent #<n>`; `Refine #<n>` for `refine.yml`).
- Preview app: `ci.yml` deploys it with `PULL_REQUEST=<n>`, which shows a bar with the pull
  request, its checks, a feedback field (posts a `## Human feedback` comment) and a merge button
  (squash). The app merges only when `build` and `review` are green on the head commit, because
  the token belongs to an admin and could bypass the branch protection.

Without `GITHUB_TOKEN` the ideas are hidden; without `PULL_REQUEST` the bar is.

One-time setup: create a fine-grained personal access token at
https://github.com/settings/personal-access-tokens/new, resource owner `FOURTEEN-AI`, only the
repository `FOURTEEN-AI/notes`, permissions Issues, Pull requests and Contents: read and write.
The repository is public, so reading check runs and workflow runs needs no further permission.

```sh
fly secrets set --app notes-fourteen-it GITHUB_TOKEN=<token>
GH_TOKEN="$GH_TOKEN_FOURTEEN_AI" gh secret set APP_GITHUB_TOKEN --repo FOURTEEN-AI/notes
```

One-time setup:

```sh
# Install the Claude GitHub App (https://github.com/apps/claude) on the repository, so that the
# agent's pushes start CI. Then, in a terminal inside the devcontainer, create a token that is
# paid by the Claude subscription and store it as the secret CLAUDE_CODE_OAUTH_TOKEN:
claude setup-token
GH_TOKEN="$GH_TOKEN_FOURTEEN_AI" gh secret set CLAUDE_CODE_OAUTH_TOKEN --repo FOURTEEN-AI/notes

# Fly org token for the preview jobs. Production keeps its own, narrower FLY_API_TOKEN.
fly tokens create org | GH_TOKEN="$GH_TOKEN_FOURTEEN_AI" gh secret set FLY_ORG_TOKEN --repo FOURTEEN-AI/notes
GH_TOKEN="$GH_TOKEN_FOURTEEN_AI" gh variable set FLY_ORG --repo FOURTEEN-AI/notes --body <org-slug>

# The login of production, once more, for the previews (Fly secrets cannot be read back).
GH_TOKEN="$GH_TOKEN_FOURTEEN_AI" gh secret set APP_LOGIN_NAME --repo FOURTEEN-AI/notes
GH_TOKEN="$GH_TOKEN_FOURTEEN_AI" gh secret set APP_LOGIN_PASSWORD --repo FOURTEEN-AI/notes

# Jev (TypeSafe) judges the stage and fake methods in the `acceptance` workflow. Own key with its own
# budget; no other workflow uses it.
GH_TOKEN="$GH_TOKEN_FOURTEEN_AI" gh secret set TYPESAFE_API_KEY --repo FOURTEEN-AI/notes

# Last, once the checks `build`, `review` and `acceptance` have run on a pull request: protect main.
# "enforce_admins": false keeps direct pushes to main open for admins (local sessions).
GH_TOKEN="$GH_TOKEN_FOURTEEN_AI" gh api -X PUT repos/FOURTEEN-AI/notes/branches/main/protection --input - <<'EOF'
{
  "required_status_checks": {"strict": false, "contexts": ["build", "review", "acceptance"]},
  "enforce_admins": false,
  "required_pull_request_reviews": {"required_approving_review_count": 0},
  "restrictions": null
}
EOF
```

## Configuration

| Name | Where | Purpose |
|---|---|---|
| `DATABASE_URL` | Fly secret (set by `fly postgres attach`) | PostgreSQL connection |
| `SPRING_SECURITY_USER_NAME`, `SPRING_SECURITY_USER_PASSWORD` | Fly secrets | the one login |
| `FLY_API_TOKEN` | GitHub Actions secret | deploy from CI |
| `FLY_ORG_TOKEN`, variable `FLY_ORG` | GitHub Actions secret and variable | preview apps |
| `APP_LOGIN_NAME`, `APP_LOGIN_PASSWORD` | GitHub Actions secrets | login of the preview apps |
| `CLAUDE_CODE_OAUTH_TOKEN` | GitHub Actions secret | coding and review agent |
| `TYPESAFE_API_KEY` | GitHub Actions secret (own TypeSafe key and budget; only the `acceptance` job uses it) | Jev judges the changed stage and fake methods |
| `GITHUB_TOKEN` | Fly secret (production; set by `ci.yml` on previews) | ideas and preview bar |
| `APP_GITHUB_TOKEN` | GitHub Actions secret | the same token, for the preview apps |
| `PULL_REQUEST` | Fly env of a preview (set by `ci.yml`) | turns on the preview bar |
| `PULL_REQUEST_SHA` | Fly env of a preview (set by `ci.yml`) | lets the bar offer a reload after a new deploy |
