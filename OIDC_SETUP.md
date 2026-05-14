# OIDC Setup Guide

Pubgity delegates authentication to any **OpenID Connect**-compliant identity provider.
Roles (`ADMIN`, `MODERATOR`, `USER`) are sourced from a configurable claim in the ID token
and synced into Pubgity's local user store on every login.

> **For local development** see [LOCAL_DEV.md](LOCAL_DEV.md) — a pre-configured Keycloak
> realm with 5 seed users is imported automatically.

---

## How roles work

| Role        | Access                                                              |
|-------------|---------------------------------------------------------------------|
| `ADMIN`     | Full access — admin panel, all jobs, all players                    |
| `MODERATOR` | Jobs page scoped to assigned players, subject to queue size limit   |
| `USER`      | Public pages + personal home/profile                                |

- Roles are read from the ID token claim configured via `pubgity.oidc.roles-claim`
  (default: `realm_access.roles`).
- Pubgity syncs the local `AppUser` roles on every login.
- Role changes take effect on the user's **next login**.
- Moderator constraints (allowed players, max queue size) are configured in Pubgity's
  admin panel at `/admin/users` — they are not managed in the identity provider.

---

## Generic OIDC Setup

### Step 1 — Register a client / application

Create a new **confidential** (server-side) OpenID Connect client in your provider:

| Setting                  | Value                                              |
|--------------------------|----------------------------------------------------|
| Client type              | `OpenID Connect` / `OAuth 2.0`                     |
| Client ID                | any — e.g. `pubgity`                               |
| Grant type               | `Authorization Code`                               |
| Valid redirect URI       | `https://<your-host>/login/oauth2/code/oidc`       |
| Post-logout redirect URI | `https://<your-host>/`                             |

Copy the **Client ID** and **Client Secret** for the environment variables below.

### Step 2 — Ensure required claims are present in the ID token

Pubgity requires the following claims to be present:

| Claim                   | Purpose                                                               |
|-------------------------|-----------------------------------------------------------------------|
| `sub`                   | Stable unique user identifier — must never change for a given user    |
| `email`                 | Synced to Pubgity on every login                                      |
| `preferred_username`    | Display name (falls back to `name`, `email`, then `sub`)             |
| Roles claim (see below) | List of role name strings that map to `ADMIN`, `MODERATOR`, `USER`   |
| `end_session_endpoint`  | Required for RP-initiated logout (must be in the provider's discovery document) |

### Step 3 — Configure the roles claim

By default Pubgity reads roles from `realm_access.roles` (Keycloak's nested structure).
Set `pubgity.oidc.roles-claim` to the dot-separated path of the claim in your ID token:

| Provider        | Typical claim path   |
|-----------------|----------------------|
| Keycloak        | `realm_access.roles` |
| Auth0           | `roles`              |
| Okta            | `roles`              |
| Microsoft Entra | `roles`              |

**application.yml example:**
```yaml
pubgity:
  oidc:
    roles-claim: roles   # flat top-level claim
```

If your provider uses a custom namespace (e.g. Auth0 namespace URIs), configure
the exact claim key that contains the role string list.

If no recognised role is found for a user they default to `USER`.

### Step 4 — Set environment variables

| Variable             | Description               | Example                        |
|----------------------|---------------------------|--------------------------------|
| `OIDC_ISSUER_URI`    | Provider issuer URL       | `https://accounts.example.com` |
| `OIDC_CLIENT_ID`     | Client ID from Step 1     | `pubgity`                      |
| `OIDC_CLIENT_SECRET` | Client secret from Step 1 | `supersecret`                  |

**Docker Compose example:**
```yaml
services:
  pubgity:
    environment:
      OIDC_ISSUER_URI: "https://accounts.example.com"
      OIDC_CLIENT_ID: "pubgity"
      OIDC_CLIENT_SECRET: "supersecret"
```

Pubgity uses Spring Security's OIDC auto-configuration — the discovery document at
`<OIDC_ISSUER_URI>/.well-known/openid-configuration` must be reachable at startup.

---

## Keycloak-Specific Setup

> The local development environment ships with a pre-configured realm — the steps below
> are for a fresh **production** Keycloak installation.

### Step 1 — Create a Realm

1. Log in to the Keycloak admin console.
2. Click the realm selector (top-left) → **Create Realm**.
3. Set **Realm name**: `pubgity` (or any name — update `OIDC_ISSUER_URI` accordingly).
4. Click **Create**.

`OIDC_ISSUER_URI` format: `https://<keycloak-host>/realms/<realm-name>`

### Step 2 — Create realm roles

1. Go to **Realm roles → Create role**.
2. Create three roles (names must match exactly):
   - `ADMIN`
   - `MODERATOR`
   - `USER`

### Step 3 — Create a Client

1. Go to **Clients → Create client**.
2. Fill in:

   | Field       | Value            |
   |-------------|------------------|
   | Client type | `OpenID Connect` |
   | Client ID   | `pubgity`        |

3. Click **Next**.
4. Enable **Client authentication** (confidential client), disable **Direct access grants**.
5. Click **Next**.
6. Set **Valid redirect URIs**: `https://<your-host>/login/oauth2/code/oidc`
7. Set **Valid post-logout redirect URIs**: `https://<your-host>/`
8. Set **Web origins**: `https://<your-host>`
9. Click **Save**.
10. Go to the **Credentials** tab and copy the **Client secret**.

### Step 4 — Add the realm-roles protocol mapper

This mapper injects realm roles into the ID token so Pubgity can read them.

1. In your client, go to the **Client scopes** tab → click `pubgity-dedicated`.
2. Click **Add mapper → By configuration → User Realm Role**.
3. Fill in:

   | Field               | Value                |
   |---------------------|----------------------|
   | Name                | `realm roles`        |
   | Token Claim Name    | `realm_access.roles` |
   | Add to ID token     | `On`                 |
   | Add to access token | `On`                 |
   | Add to userinfo     | `On`                 |
   | Multivalued         | `On`                 |

4. Click **Save**.

`pubgity.oidc.roles-claim` must be set to `realm_access.roles` (this is the default).

### Step 5 — Assign roles to users

1. Go to **Users** → click a user → **Role mapping** tab.
2. Click **Assign role** → filter by **realm roles**.
3. Select `ADMIN`, `MODERATOR`, or `USER` as appropriate.

### Step 6 — Set environment variables

```yaml
services:
  pubgity:
    environment:
      OIDC_ISSUER_URI: "https://keycloak.example.com/realms/pubgity"
      OIDC_CLIENT_ID: "pubgity"
      OIDC_CLIENT_SECRET: "supersecret"
```

