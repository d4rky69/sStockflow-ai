# Supabase authentication setup

StockFlow now supports Supabase email/password authentication, persistent browser sessions, automatic access-token refresh, a protected dashboard shell, and sign-out. Angular API calls also forward the Supabase access token in the `Authorization` header.

## 1. Create or select a Supabase project

In Supabase, open **Project Settings → API** and copy:

- Project URL, such as `https://your-project-ref.supabase.co`
- Browser-safe publishable key, normally beginning with `sb_publishable_`

The legacy `anon` key also works. Never use a secret key or `service_role` key in this Angular application.

## 2. Configure StockFlow

Edit:

```text
src/assets/config/runtime-config.js
```

Set:

```javascript
window.__stockflowConfig = {
  supabaseUrl: 'https://your-project-ref.supabase.co',
  supabasePublishableKey: 'sb_publishable_your_key'
};
```

This file is loaded before Angular starts and is copied to the production build. The publishable key is intended for browser use; authorization must still be enforced with Supabase policies and backend token validation.

## 3. Configure authentication URLs

In **Authentication → URL Configuration**, set:

```text
Site URL: https://stockflow-ai-oveyj.pages.dev
```

Add redirect URLs:

```text
http://localhost:4200
https://stockflow-ai-oveyj.pages.dev
```

## 4. Configure email authentication

In **Authentication → Providers → Email**:

- Enable Email authentication.
- Keep email confirmation enabled for production.
- For an invite-only pilot, disable open sign-ups after creating or inviting the approved users.

Create the first user in **Authentication → Users**, or use the login page's account-creation form while open sign-ups are enabled.

## 5. Run locally

```cmd
npm start
```

Open `http://localhost:4200`, create or sign in to an account, refresh the page to verify session restoration, and use the profile menu to sign out.

## 6. Build

```cmd
npm run build
```

## Production security boundary

The frontend now forwards:

```http
Authorization: Bearer <supabase-access-token>
```

The existing StockFlow backend must validate this JWT before login becomes a complete authorization boundary. It currently uses `X-Tenant-ID` for tenant selection; production hardening should validate the Supabase token and verify that the authenticated user is allowed to access the requested tenant. Do not treat the tenant header alone as authorization.
