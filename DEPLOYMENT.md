# Deployment (CI/CD)

pubgity is deployed as a Docker image, built by GitHub Actions and pushed to
**GitHub Container Registry (GHCR)**. An existing application on a
self-hosted Coolify instance is then updated to the new image tag and
restarted. Database/infra are configured directly in Coolify and are **not**
touched by these workflows.

## Pipelines

| Workflow | Trigger | What it does |
|---|---|---|
| `.github/workflows/ci.yml` | push to any branch except `main`, PRs | `./gradlew test` only |
| `.github/workflows/release-please.yml` | push to `main` | Runs tests, then (a) builds & pushes a Docker image to GHCR tagged `sha-<short-sha>` + `edge`, and (b) runs [release-please](https://github.com/googleapis/release-please-action), which parses commits and maintains a release PR. Merging that PR bumps the version, tags the commit, and publishes a GitHub Release. |
| `.github/workflows/release-published.yml` | GitHub Release **published** | **Promotes** the `sha-<sha>` image already built for that commit to the version tag + `latest` (no rebuild), then calls the Coolify API to point the application at the new tag and restarts it. Falls back to building from source if that image is missing. |

## Image strategy: build once, promote on release

Every push to `main` builds exactly one image, tagged with the commit SHA
(`sha-<short-sha>`, immutable) and `edge` (floating "latest main", handy for
ad-hoc/staging pulls). When a release is published, `release-published.yml`
does **not** rebuild the image — it uses `docker buildx imagetools create` to
copy that same image's manifest to the version tag (and `latest`). This
guarantees the exact artifact that was tested on `main` is what gets
deployed, and avoids paying for a redundant build at release time.

If the expected `sha-<sha>` image can't be found in GHCR (e.g. pruned by a
registry retention/cleanup policy), the workflow logs a `::warning::`
annotation and falls back to building the image from source for that tag
instead of failing the release outright.

## Commit message convention (Conventional Commits)

Version bumps are driven entirely by commit messages on `main` (or PR titles,
if you squash-merge):

- `fix: ...` → patch bump (`1.0.0` → `1.0.1`)
- `feat: ...` → minor bump (`1.0.0` → `1.1.0`)
- `feat!: ...` or a footer containing `BREAKING CHANGE: ...` → major bump (`1.0.0` → `2.0.0`)
- Other prefixes (`chore:`, `docs:`, `ci:`, `refactor:`, `test:`) are tracked in the changelog but don't bump the version.
- Commits that don't follow the convention are ignored for versioning.
- Only the **highest-severity** change since the last release is applied once (multiple `feat!:` commits still cause a single major bump, not multiple).

release-please opens/updates a standing "release PR" on every qualifying
push to `main`. **Merging that PR** is what actually bumps `build.gradle.kts`
(via its `// x-release-please-version` annotation) and `version.txt`, creates
the git tag (`vX.Y.Z`), and publishes the GitHub Release — which in turn
triggers `release-published.yml`.

## One-time setup

### 1. GitHub repository secrets

| Secret | Description |
|---|---|
| `RELEASE_PLEASE_TOKEN` | A GitHub Personal Access Token (classic, `repo` scope, or fine-grained with `Contents: Read & write` + `Pull requests: Read & write`) belonging to a user/bot with push access. **Required** — release/tag creation via the default `GITHUB_TOKEN` does not trigger other workflows (GitHub's recursive-run prevention), so `release-please.yml` would otherwise never fire `release-published.yml`. |
| `COOLIFY_URL` | Base URL of your Coolify instance, e.g. `https://coolify.example.com` |
| `COOLIFY_TOKEN` | Coolify API token (Coolify → Settings → API Tokens) |
| `COOLIFY_APP_UUID` | UUID of the existing pubgity application in Coolify |

No secrets are needed to push to GHCR — authentication uses the automatic
`GITHUB_TOKEN`.

### 2. Coolify application setup (done once, manually)

1. Create the application in Coolify with build pack **Docker Image**.
2. Point it at `ghcr.io/wukash-r/pubgity` (initial tag can be `latest`).
3. If the GHCR package is private, add a registry credential in Coolify (a
   GitHub PAT with `read:packages`) so Coolify can pull it.
4. Configure all environment variables (Mongo URI, OIDC/Keycloak settings,
   etc.) and any linked services (MongoDB, etc.) directly in Coolify — these
   are **not** managed by GitHub Actions.
5. Note the application's UUID (visible in the Coolify URL or via the API)
   and store it as the `COOLIFY_APP_UUID` secret above.

### 3. GHCR package visibility

By default, new GHCR packages are private. Either make the package public,
or configure Coolify with a PAT that has `read:packages` for the private
image.

## Release flow summary

1. Merge PRs into `main` with Conventional Commit messages.
2. release-please keeps a release PR up to date with the pending version bump and changelog.
3. When ready to ship, merge the release PR → tag + GitHub Release are created automatically.
4. `release-published.yml` promotes the already-built image for that commit to the version tag on GHCR, and updates + restarts the app on Coolify.

No manual Coolify interaction is needed after the one-time setup above.
