# Frontend Integration Guide — StoryLegends Backend

## Base URL
- Local: `http://localhost:8080`
- Production: configure via env

## Auth Header
All protected endpoints require:
```
Authorization: Bearer <token>
```

---

## Auth Endpoints (`/api/auth`)

### Register
```
POST /api/auth/register
{
  "username": "player1",
  "password": "SecurePass123!",
  "email": "player@example.com",
  "discordNickname": "player",
  "minecraftNickname": "Player123",
  "recaptchaToken": "TOKEN_FROM_GOOGLE"
}
→ 200: { "status": "success" }   // email verification required
→ 400: { "error": "..." }
→ 429: rate limited
```

### Verify Email
```
POST /api/auth/verify-email
{ "token": "token-from-email" }
→ 200: { "status": "success", "token": "<jwt>", "alreadyVerified": false }
```

### Resend Verification
```
POST /api/auth/resend-verification
{ "email": "player@example.com" }
→ 200: { "status": "success" }
```
**Rate limit: max 3 emails/day per IP.**

### Login
```
POST /api/auth/login
{
  "username": "player1",
  "password": "SecurePass123!",
  "totpCode": "123456"    // required only if 2FA enabled
}
→ 200: { "token": "<jwt>", "emailVerified": true, "username": "...", "isPlayer": true }
→ 400: { "error": "...", "totpRequired": true }
```

### Forgot Password
```
POST /api/auth/forgot-password
{ "email": "player@example.com" }
→ 200: { "message": "..." }   // always 200
```

### Reset Password
```
POST /api/auth/reset-password
{ "token": "token-from-email", "newPassword": "NewSecurePass123!" }
→ 200: { "message": "Пароль успешно изменен." }
→ 400: { "error": "..." }
```

---

## User Endpoints (`/api/users`)

### Get Current User
```
GET /api/users/me   → UserResponse
```

### Update Profile / Change Password
```
PATCH /api/users/me
{
  "email": "new@example.com",
  "discordNickname": "newNick",
  "minecraftNickname": "NewMC",
  "avatarUrl": "http://...",
  "bio": "My bio",
  "oldPassword": "CurrentPass123!",
  "newPassword": "NewPass123!"
}
→ UserResponse
```

### Get Accepted Players (public)
```
GET /api/users   → [ UserResponse, ... ]   // only isPlayer=true users
```

---

## UserResponse Object
```json
{
  "id": 1,
  "username": "player1",
  "email": "player@example.com",
  "discordNickname": "player",
  "minecraftNickname": "Player123",
  "role": "ROLE_USER",
  "avatarUrl": "http://...",
  "banned": false,
  "banReason": null,
  "emailVerified": true,
  "totpEnabled": false,
  "bio": "...",
  "isPlayer": true,
  "discordUserId": "123456789",
  "badges": [
    { "id": 1, "name": "Builder", "color": "#f4a127", "svgIcon": "<svg>...</svg>", "createdAt": "..." }
  ]
}
```
> `@SL` Discord role is **not** visible in API — it's Discord-only.
> Security fields (`registrationIp`, `lastLoginIp*`, etc.) only appear in admin `/api/admin/users`.

---

## Applications (`/api/applications`)

### Create Application
```
POST /api/applications
Authorization: Bearer <token>
{
  "firstName": "Ivan", "lastName": "Ivanov",
  "whyUs": "...", "source": "...",
  "makeContent": false,
  "additionalInfo": "...",   // minimum 200 characters
  "selfRating": 8,           // 1-10
  "recaptchaToken": "TOKEN"
}
→ 200: ApplicationResponse
→ 400: "У вас уже есть активная заявка в статусе PENDING"
→ 400: "Вы должны подтвердить свой email перед подачей заявки"
→ 400: "Вы должны быть участником нашего Discord сервера..." (if Discord bot enabled)
```
**Rules:** email verified, only 1 PENDING at a time, `additionalInfo` ≥ 200 chars, must be in Discord server.

### Get My Applications (full history)
```
GET /api/applications/my   → [ ApplicationResponse, ... ]   // newest first
```

### ApplicationResponse
```json
{
  "id": 1, "firstName": "Ivan", "lastName": "Ivanov",
  "whyUs": "...", "source": "...", "makeContent": false,
  "additionalInfo": "...", "selfRating": 8,
  "status": "PENDING",    // PENDING | ACCEPTED | REJECTED
  "adminComment": null, "createdAt": "...",
  "user": { "id": 1, "username": "...", "email": "...", "discordNickname": "...", "minecraftNickname": "...", "avatarUrl": "..." }
}
```

---

## File Uploads (`/api/files`)
```
POST   /api/files/upload/avatar    multipart/form-data  →  { "url": "http://..." }
DELETE /api/files/avatar
```

---

## TOTP / 2FA (`/api/totp`)
```
POST /api/totp/setup      → { "secret": "...", "qrCodeDataUri": "..." }
POST /api/totp/verify     { "code": "123456" }   // activates 2FA
POST /api/totp/disable    { "code": "123456" }   // disables 2FA
GET  /api/totp/status     → { "totpEnabled": true/false }
```

---

## Admin Endpoints (`/api/admin`) — ROLE_ADMIN only

### Applications
```
GET   /api/admin/applications?status=PENDING
PATCH /api/admin/applications/{id}/status
      { "status": "ACCEPTED", "adminComment": "..." }
      // ACCEPTED → isPlayer=true + email + Discord DM + @SL role assigned
      // REJECTED → email + Discord DM
```

### Users
```
GET    /api/admin/users                       // includes security dossier
POST   /api/admin/users                       { username, password, email, discordNickname, minecraftNickname, bio, emailVerified }
PATCH  /api/admin/users/{id}                  { username?, email?, discordNickname?, minecraftNickname?, bio?, isPlayer? }
DELETE /api/admin/users/{id}
POST   /api/admin/users/{id}/ban              { "reason": "..." }
POST   /api/admin/users/{id}/unban
POST   /api/admin/users/{id}/reset-password   → { "temporaryPassword": "..." }
```

**Admin-only security fields in UserResponse:**
```json
{
  "registrationIp": "RU,Moscow,78.10.162.140",
  "registrationUserAgent": "Mozilla/5.0...",
  "lastLoginIp1": "DE,Berlin,91.20.5.1",
  "lastLoginUserAgent1": "Mozilla/5.0...",
  "lastLoginIp2": "RU,Moscow,78.10.162.140",
  "lastLoginUserAgent2": "..."
}
```
Format: `CC,City,IP` (CountryCode,City,RawIP). For local/private IPs — just the raw IP.
These are **read-only** (cannot be edited via PATCH).

### Badges
```
GET    /api/admin/badges                              // list all badges
POST   /api/admin/badges                             { "name": "Builder", "color": "#f4a127", "svgIcon": "<svg>...</svg>", "discordRoleId": "123..." }
PATCH  /api/admin/badges/{id}                        { same fields, all optional }
DELETE /api/admin/badges/{id}
POST   /api/admin/users/{userId}/badges/{badgeId}    // assign badge to user + sync Discord role
DELETE /api/admin/users/{userId}/badges/{badgeId}    // remove badge + sync Discord role
```
Each badge has: `name`, `color` (hex), `svgIcon` (SVG string for website), `discordRoleId` (auto-synced).
Badges are reusable — create once, apply to many users.

---

## Discord Integration
When `discord.bot.enabled=true`:
- **Application submit** → checks user is in Discord guild by `discordNickname`
- **Application accepted** → assigns `@SL` role + sends DM to user
- **Application rejected** → sends DM with reason
- **Application submitted** → sends DM confirmation
- **Badge assigned/removed** → syncs Discord role automatically

`@SL` role is **not** exposed in API. Show only `badges` on website.

---

## Ban Handling
- Banned users CAN login and receive JWT
- After login check `banned` field → show banner with `banReason`, read-only profile
- Disable: creating applications, editing profile, uploading avatar

## Statuses & Roles
- `ApplicationStatus`: `PENDING` | `ACCEPTED` | `REJECTED`
- `UserRole`: `ROLE_USER` | `ROLE_MODERATOR` | `ROLE_ADMIN`

## Errors
- `400` → `{ "error": "..." }`
- `401/403` → redirect to login
- `429` → rate limited
