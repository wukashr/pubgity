# Pubgity

Pubgity is a web app for tracking PUBG player and match statistics. It pulls data from the official PUBG API and stores it in MongoDB, with player profiles, recent match details, lifetime and season charts, and role-based access to imports and administration.

## Features

- Browse PUBG players and match history
- View match participants and aggregated performance
- Link a PUBG player to your signed-in profile
- Track lifetime and season statistics
- Queue and monitor data imports
- Manage users and moderator limits
- Reducing amount of API calls due to strict PUBG API rate limits

## Local development

You need Docker with Compose and JDK 24.

Start MongoDB and Keycloak:

```bash
docker compose up -d
docker compose -f docker-compose.keycloak.yml up -d
```

Copy `src/main/resources/application-local.example.yml` to `src/main/resources/application-local.yml` Set `pubg.api.key` in the local file to your PUBG API key, then start Pubgity:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Open <http://localhost:8080>.

The local Keycloak setup has seed users and a ready-made realm. Full instructions are in [LOCAL_DEV.md](LOCAL_DEV.md).

## Configuration

Pubgity uses:

- MongoDB
- A PUBG Developer API key
- An OpenID Connect identity provider

Production OIDC settings include `OIDC_ISSUER_URI`, `OIDC_CLIENT_ID`, and `OIDC_CLIENT_SECRET`. For provider setup and role mappings, see [OIDC_SETUP.md](OIDC_SETUP.md).

Deployment details and the GitHub Actions workflow are described in [DEPLOYMENT.md](DEPLOYMENT.md).

## Tests

```bash
./gradlew test
```
