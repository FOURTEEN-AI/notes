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

Production is built and kept by `.github/workflows/infra.yml` (`infra/reconcile.sh`, see
`docs/plan.md`, step 10): on a push that changes `infra/` or `fly.toml`, every night, and on
request (Actions, "Infra", "Run workflow"). It creates the Fly app, the unmanaged Postgres
(`notes-fourteen-it-db`, 512 MB, snapshots for 30 days), the secrets, the `CNAME` `notes` at IONOS
and the certificate for https://notes.fourteen-it.de, removes the extra machine of the first
deploy, and repairs drift. Changes and failures land in the issue "Infrastructure drift".

A human provides these once, as GitHub secrets (and the variable `FLY_ORG`), and nothing else:

| Name | What |
| --- | --- |
| `FLY_ORG_TOKEN` | Fly org token (`fly tokens create org`) |
| `FLY_API_TOKEN` | Fly deploy token of the app, for `ci.yml` (`fly tokens create deploy --app notes-fourteen-it`) |
| `APP_LOGIN_NAME`, `APP_LOGIN_PASSWORD` | the one login of the app |
| `APP_GITHUB_TOKEN` | the fine-grained GitHub token of the app |
| `IONOS_API_KEY` | key of the IONOS hosting DNS API (`<prefix>.<secret>`) |
| `INFRA_GITHUB_TOKEN` | GitHub token with administration rights, for `infra/github.sh` |

A rotated secret takes effect with the next run of `infra.yml` and the next deploy.

The app runs on one machine (`--ha=false` in CI). `infra/restore-check.sh` restores the latest
snapshot of the database into a scratch Postgres every Monday night and fails if it does not work.

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

Store it as the GitHub secret `APP_GITHUB_TOKEN`; `infra.yml` sets it on the Fly app.

The previews run code of pull requests, so they get their own, second token of the same kind and
a login that is not the one of production:

```sh
GH_TOKEN="$GH_TOKEN_FOURTEEN_AI" gh secret set PREVIEW_GITHUB_TOKEN --repo FOURTEEN-AI/notes
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

# A login for the previews, never the one of production: a preview runs code of a pull request.
GH_TOKEN="$GH_TOKEN_FOURTEEN_AI" gh secret set PREVIEW_LOGIN_NAME --repo FOURTEEN-AI/notes
GH_TOKEN="$GH_TOKEN_FOURTEEN_AI" gh secret set PREVIEW_LOGIN_PASSWORD --repo FOURTEEN-AI/notes

# Jev (TypeSafe) judges the stage and fake methods in the `acceptance` workflow. Own key with its own
# budget; no other workflow uses it.
GH_TOKEN="$GH_TOKEN_FOURTEEN_AI" gh secret set TYPESAFE_API_KEY --repo FOURTEEN-AI/notes

# Last, once the checks `build`, `secret-scan`, `review`, `acceptance` and `smells` have run on a pull request: protect main.
# "enforce_admins": false keeps direct pushes to main open for admins (local sessions).
GH_TOKEN="$GH_TOKEN_FOURTEEN_AI" gh api -X PUT repos/FOURTEEN-AI/notes/branches/main/protection --input - <<'EOF'
{
  "required_status_checks": {"strict": false, "contexts": ["build", "secret-scan", "review", "acceptance", "smells"]},
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
| `PREVIEW_LOGIN_NAME`, `PREVIEW_LOGIN_PASSWORD` | GitHub Actions secrets | login of the preview apps (not the one of production) |
| `CLAUDE_CODE_OAUTH_TOKEN` | GitHub Actions secret | coding and review agent |
| `TYPESAFE_API_KEY` | GitHub Actions secret (own TypeSafe key and budget; only the `acceptance` job uses it) | Jev judges the changed stage and fake methods |
| `GITHUB_TOKEN` | Fly secret (production; set by `ci.yml` on previews) | ideas and preview bar |
| `PREVIEW_GITHUB_TOKEN` | GitHub Actions secret | a second token of the same kind, for the preview apps |
| `PULL_REQUEST` | Fly env of a preview (set by `ci.yml`) | turns on the preview bar |
| `PULL_REQUEST_SHA` | Fly env of a preview (set by `ci.yml`) | lets the bar offer a reload after a new deploy |

## Keeping secrets out of the repository

- GitHub secret scanning with push protection is on for the repository (Settings, Code security).
  It blocks a push that contains a known token.
- `.gitleaks.toml` adds the tokens of this project. The devcontainer installs gitleaks and the hook
  `.githooks/pre-commit` blocks a commit with a secret. Elsewhere, run
  `.devcontainer/install-gitleaks.sh` and `git config core.hooksPath .githooks`.
- `image-scan` in `ci.yml` scans the container image (OS packages, Dockerfile) with Trivy; locally
  `./gradlew build` and then `.github/image-scan.sh` (needs Docker and
  `.devcontainer/install-trivy.sh`). Accepted findings: `.trivyignore`, with a reason and an expiry.
- The check `secret-scan` in `ci.yml` scans every commit and the description and comments of the
  pull request.
- The app masks the values of environment variables whose name contains PASSWORD, TOKEN, SECRET,
  KEY or `DATABASE_URL` in its logs.

**A secret that was pushed is compromised.** The repository is public and the history is copied
within seconds, so removing the commit does not help. Revoke and replace it at once (Fly token:
`fly tokens revoke`, GitHub token: its settings page, `CLAUDE_CODE_OAUTH_TOKEN`: `claude
setup-token`, login or database password: set a new one), update the secret where it is used (see
the table above), and only then clean up the history. GitHub's secret scanning and `secret-scan`
only tell you; they do not check whether the secret still works.
