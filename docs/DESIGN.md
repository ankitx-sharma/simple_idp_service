# Custom Authorization Server (OAuth2-style) — Design Document

## 1. Purpose

Build a learning-focused, portfolio-grade **Authorization Server** that issues and manages tokens in an OAuth2-style architecture.

This project is designed to demonstrate:
- Authentication + token issuance separation from resource APIs
- Secure JWT issuance & validation model
- Refresh token lifecycle (rotation/revocation)
- Client trust model (client registration + allowed flows)
- Key management via JWKS + rotation
- Practical security hardening (rate limiting, abuse prevention, audit logs)

## 2. Scope

### 2.1 In-scope
- User authentication (username/password)
- Client registration and trust rules
- Token issuance (access + refresh)
- JWKS endpoint for public keys
- Refresh token rotation + revocation
- Minimal OAuth2-style endpoints:
  - Token endpoint supporting at least Password + Refresh Token grants
  - Optional: Client Credentials grant (for service-to-service)
- Token introspection (optional, for opaque tokens or debugging)
- Basic authorization concepts: scopes/roles as claims

### 2.2 Out-of-scope (Non-goals)
- Full IAM suite (SCIM, HR provisioning, org charts, complex admin UI)
- SAML
- LDAP/AD federation
- Social logins (Google/GitHub)
- Full authorization policy engine (ABAC with complex rules)
- Multi-tenant enterprise features (may be added later)

### 2.3 Technology & Constraints
- Runtime: Java 21, Spring Boot 3.x
- Security: Spring Security (PasswordEncoder + request security hardening later)
- Persistence: PostgreSQL (users, clients, refresh_tokens, audit_log)
- Migrations: Flyway (schema versioning)
- JWT: RS256 signing + JWKS publishing (library: [choose Nimbus or JJWT])
- Rate limiting: Redis (phase 2) for counters/limits (not a source of truth)

## 3. Terminology

- **Authorization Server (AS)**: issues tokens and manages sessions/refresh tokens.
- **Resource Server (RS)**: backend APIs that validate tokens and enforce access.
- **Client**: an application that requests tokens (web app, mobile app, service).
- **Principal**: the authenticated user (or service identity in client-credentials).
- **Access Token**: short-lived JWT used to access APIs.
- **Refresh Token**: long-lived token used to obtain new access tokens.
- **JWKS**: JSON Web Key Set endpoint publishing public keys for JWT validation.

## 4. Actors and Responsibilities

### 4.1 User
- Logs in via credentials
- Receives tokens (via client app)

### 4.2 Client Application
- Calls AS to obtain tokens
- Stores tokens (securely) and uses access token to call APIs
- Uses refresh token to obtain new access tokens

### 4.3 Authorization Server (this project)
- Authenticates users
- Issues tokens with correct claims
- Enforces client trust rules (allowed grants, redirect URIs if applicable)
- Rotates and revokes refresh tokens
- Publishes JWKS for token verification by Resource Servers

### 4.4 Resource Server(s) (separate sample app or module)
- Validates JWT signature + issuer/audience + expiry
- Enforces scopes/roles for endpoints

## 5. High-Level Architecture

```
+---------+ +-------------------+ +-------------------+
| User |<------->| Client App |<------->| Authorization |
| | Login | (Web/Mobile/API) | Tokens | Server (this) |
+---------+ +-------------------+ +-------------------+
|
| JWKS (public keys)
v
+-------------------+
| Resource Server(s) |
| (APIs) |
+-------------------+
```


## 6. Core Modules

### 6.1 Identity Module (Users)
Responsibilities:
- User registration (optional)
- Password hashing & verification
- Account status flags:
  - enabled/disabled
  - locked (after abuse)
  - emailVerified (optional)

Data:
- users table (or collection)

### 6.2 Client Trust Module (OAuth Clients)
Responsibilities:
- Register clients
- Store and validate:
  - client_id
  - client_secret (hashed)
  - allowed grant types
  - allowed scopes
  - token lifetimes overrides (optional)
  - redirect URIs (only if you add auth-code flow later)

Data:
- clients table

### 6.3 Token Service
Responsibilities:
- Create signed JWT access tokens
- Create refresh tokens (random, high entropy)
- Persist refresh tokens with metadata
- Enforce refresh token rotation & reuse detection
- Token introspection (optional)

Data:
- refresh_tokens table
- revoked_tokens table (optional, only if needed)
- audit_log table

### 6.4 Key Management Module
Responsibilities:
- Maintain active signing key (private)
- Publish JWKS (public)
- Rotate keys safely (keep old public keys for validation until TTL passes)

Data:
- keys table (optional) or keystore file + metadata

#### Key Rotation Policy
- Maintain 1 active signing key + keep old public keys published until all tokens signed by them have expired (>= access token TTL window).
- Include kid in JWT header to support rotation.

## 7. Data Model (Minimal)

### 7.1 users
- id (UUID)
- email (unique)
- password_hash
- enabled (bool)
- locked_until (datetime nullable)
- created_at, updated_at

### 7.2 clients
- id (UUID)
- client_id (public identifier)
- client_secret_hash
- name
- allowed_grants (set)
- allowed_scopes (set)
- access_token_ttl_seconds
- refresh_token_ttl_seconds
- created_at, updated_at

### 7.3 refresh_tokens
- id (UUID)
- token_hash (store hash, never store raw token)
- user_id (nullable for client-credentials)
- client_id
- issued_at
- expires_at
- revoked_at (nullable)
- replaced_by_token_id (nullable)  // rotation chain
- reuse_detected (bool)            // if old token used after rotation
- user_agent (optional)
- ip (optional)

### 7.4 audit_log (recommended)
- id
- event_type (LOGIN_SUCCESS, LOGIN_FAIL, TOKEN_ISSUED, REFRESH_ROTATED, ...)
- user_id (nullable)
- client_id (nullable)
- timestamp
- metadata (json)

## 8. Endpoints (API Contract)
Base path: `/api`

#### Client Authentication
- Client credentials are validated on the token endpoint using:
  - Option A: HTTP Basic Auth
  - Option B: `client_id` + `client_secret` form params (for learning/demo)

### 8.1 Auth endpoints (optional UI separate)
- `POST /auth/register` (optional)
- `POST /auth/login` (optional; if you prefer token endpoint only, skip)

### 8.2 OAuth2-style token endpoint
- `POST /oauth2/token`

Supported grants (phase 1):
- `grant_type=password`
  - body: username, password, client_id, client_secret, scope(optional)
- `grant_type=refresh_token`
  - body: refresh_token, client_id, client_secret
Optional (phase 2):
- `grant_type=client_credentials`
  - body: client_id, client_secret, scope(optional)

Response (typical):
- access_token (JWT)
- token_type = "Bearer"
- expires_in
- refresh_token (only for user grants)
- scope

### 8.3 JWKS
- `GET /.well-known/jwks.json`

Returns:
- public keys for JWT validation (kid, kty, alg, n/e for RSA, etc.)

### 8.4 Well-known metadata (optional but great)
- `GET /.well-known/openid-configuration`
Even if you don't implement full OIDC, you can publish:
- issuer
- token_endpoint
- jwks_uri
- supported_grant_types
- supported_scopes

### 8.5 Introspection (optional)
- `POST /oauth2/introspect`
Used mainly for opaque tokens or admin debugging.

### 8.6 Admin APIs (minimal, can be secured)
- `POST /admin/clients` (create client)
- `GET /admin/clients/{id}`
- `POST /admin/users/{id}/disable`
Keep minimal and lock down.

## 9. JWT Design

Access token (JWT):
- Header:
  - alg: RS256 (recommended) or ES256
  - kid: key id for rotation
- Claims:
  - iss: issuer (e.g. `https://auth.local`)
  - aud: intended audience (e.g. `orders-api`)
  - sub: user id (or client id for client-credentials)
  - iat, exp
  - scope: space-separated scopes or array
  - roles: array (optional)
  - jti: unique token id (optional)

Notes:
- Access tokens are short-lived (e.g. 5–15 minutes)
- Prefer **asymmetric signing** so resource servers only need public keys (JWKS)

## 10. Refresh Token Design (DB-backed)

Rules:
- Refresh token is opaque random value (256-bit+)
- Store only **hash** of refresh token in DB
- Each refresh request:
  1) validate client
  2) validate refresh token hash exists and not revoked/expired
  3) issue new access token
  4) rotate refresh token (issue new refresh token)
  5) revoke old refresh token and link `replaced_by_token_id`

Reuse detection:
- If a revoked-but-rotated refresh token is used again:
  - mark reuse_detected=true
  - revoke the entire token family (all tokens in chain) OR revoke all active tokens for that user+client
  - create audit log event

## 11. Security Requirements

### 11.1 Passwords
- Use strong hashing (bcrypt/argon2)
- Always constant-time comparisons where relevant

### 11.2 Client secrets
- Store hashed
- Rotate secrets (optional phase 2)

### 11.3 Rate limiting and abuse protection
- Rate-limit:
  - login/token endpoint by IP and by username
- Add lockout:
  - after N failures, lock for X minutes
- Add audit logs

### 11.4 Token validation standards (resource servers)
Resource servers must verify:
- signature using JWKS
- `iss` matches expected
- `aud` matches the API
- `exp` not expired
- optionally `nbf`, `jti`

### 11.5 CORS / CSRF
- Token endpoint is typically server-to-server; if used from browser:
  - handle CORS carefully
  - auth-code + PKCE is the safer pattern (phase 3)

### 11.6 Error Response Contract
Return OAuth2-style errors:
- invalid_client → 401
- invalid_grant → 400
- invalid_scope → 400
- invalid_request → 400

## 12. Observability

- Structured logs
- Audit log table
- Metrics (optional):
  - token issuance count
  - refresh rotation count
  - login failure count
- Tracing (optional)

## 13. Deployment Model

- Single service for Authorization Server
- Database (Postgres recommended)
- Optional Redis for rate-limits
- Keys:
  - local keystore or DB key table
  - support rotation

## 14. Testing Strategy

- Unit tests:
  - password hashing and verification
  - token signing/verifying
  - refresh rotation rules and reuse detection
- Integration tests:
  - /oauth2/token success/fail cases
  - jwks endpoint
- Security tests:
  - brute force simulation on token endpoint
  - invalid client credentials
  - replayed refresh token

## 15. Milestones

### Phase 1 (Core)
- Users + Clients
- /oauth2/token (password + refresh_token)
- JWT RS256 + JWKS
- DB-backed refresh rotation

### Phase 2 (Hardening)
- Abuse protection (rate limit + lockout)
- Audit logs
- Client credentials grant (service-to-service)

### Phase 3 (Optional, advanced)
- Authorization Code + PKCE
- Well-known configuration
- Minimal admin UI
- Fine-grained scopes + consent (optional)

## 16. Reference Behaviors (Compatibility Goals)

While not claiming full OAuth2/OIDC compliance, the system should follow familiar behaviors:
- Token endpoint shapes resemble OAuth2
- JWKS format compatible with standard JWT libraries
- Claims align with common patterns (iss/aud/sub/exp)
