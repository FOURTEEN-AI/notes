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
# Creates the database and user and sets the secret DATABASE_URL on the app.
fly postgres attach notes-fourteen-it-db --app notes-fourteen-it

# The one login of the app.
fly secrets set --app notes-fourteen-it --stage \
  SPRING_SECURITY_USER_NAME=<name> SPRING_SECURITY_USER_PASSWORD=<password>

# Deploy token for CI.
fly tokens create deploy --app notes-fourteen-it \
  | GH_TOKEN="$GH_TOKEN_FOURTEEN_AI" gh secret set FLY_API_TOKEN --repo FOURTEEN-AI/notes
```

## Configuration

| Name | Where | Purpose |
|---|---|---|
| `DATABASE_URL` | Fly secret (set by `fly postgres attach`) | PostgreSQL connection |
| `SPRING_SECURITY_USER_NAME`, `SPRING_SECURITY_USER_PASSWORD` | Fly secrets | the one login |
| `FLY_API_TOKEN` | GitHub Actions secret | deploy from CI |
