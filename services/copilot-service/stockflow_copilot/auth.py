from dataclasses import dataclass
from fastapi import HTTPException, Request
from jwt import PyJWKClient, decode, get_unverified_header
from .config import (
    AUTH_DISABLED_FOR_LOCAL,
    DEFAULT_AUTHENTICATED_TENANT_ID,
    DEV_TENANT_ID,
    SUPABASE_JWT_AUDIENCE,
    SUPABASE_JWT_ISSUER,
    SUPABASE_URL,
)


@dataclass(frozen=True)
class AuthContext:
    user_id: str
    tenant_id: str
    access_token: str | None = None


def get_auth_context(request: Request) -> AuthContext:
    token = request.headers.get("authorization", "").removeprefix("Bearer ").strip() or None
    if AUTH_DISABLED_FOR_LOCAL:
        return AuthContext("local-dev", request.headers.get("x-tenant-id", DEV_TENANT_ID), token)
    if not token or not SUPABASE_URL:
        raise HTTPException(status_code=401, detail="A valid Supabase access token is required")
    try:
        algorithm = str(get_unverified_header(token).get("alg", ""))
        if algorithm not in {"ES256", "RS256"}:
            raise ValueError("Unsupported JWT signing algorithm")
        key = PyJWKClient(f"{SUPABASE_URL}/auth/v1/.well-known/jwks.json").get_signing_key_from_jwt(token)
        claims = decode(token, key.key, algorithms=[algorithm], audience=SUPABASE_JWT_AUDIENCE, issuer=SUPABASE_JWT_ISSUER)
    except Exception as exc:
        raise HTTPException(status_code=401, detail="Invalid access token") from exc
    app_metadata = claims.get("app_metadata") or {}
    tenant_id = app_metadata.get("tenant_id") or claims.get("tenant_id") or DEFAULT_AUTHENTICATED_TENANT_ID
    if not tenant_id:
        raise HTTPException(status_code=403, detail="Tenant scope is missing from the authenticated user")
    return AuthContext(str(claims.get("sub")), str(tenant_id), token)
