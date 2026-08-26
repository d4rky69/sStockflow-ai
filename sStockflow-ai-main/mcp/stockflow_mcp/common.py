import os
import httpx

CORE_API = os.getenv("STOCKFLOW_CORE_API_URL", "http://127.0.0.1:8080")
FORECAST_API = os.getenv("STOCKFLOW_FORECAST_API_URL", "http://127.0.0.1:8101")
OPTIMISATION_API = os.getenv("STOCKFLOW_OPTIMISATION_API_URL", "http://127.0.0.1:8102")
CARBON_API = os.getenv("STOCKFLOW_CARBON_API_URL", "http://127.0.0.1:8400")
TENANT_ID = os.getenv("STOCKFLOW_TENANT_ID", "TEN-ACME-PHARMA")
ACCESS_TOKEN = os.getenv("STOCKFLOW_ACCESS_TOKEN", "")

def auth_headers(tenant_id: str | None = None, access_token: str | None = None) -> dict[str, str]:
    headers = {"X-Tenant-ID": tenant_id or TENANT_ID}
    token = access_token or ACCESS_TOKEN
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers

def get_json(url: str, params: dict | None = None, tenant_id: str | None = None, access_token: str | None = None, extra_headers: dict[str, str] | None = None) -> dict | list:
    with httpx.Client(timeout=10) as client:
        headers = {**auth_headers(tenant_id, access_token), **(extra_headers or {})}
        response = client.get(url, headers=headers, params=params)
        response.raise_for_status()
        return response.json()

def post_json(url: str, payload: dict, tenant_id: str | None = None, access_token: str | None = None, extra_headers: dict[str, str] | None = None) -> dict | list:
    with httpx.Client(timeout=20) as client:
        headers = {**auth_headers(tenant_id, access_token), **(extra_headers or {})}
        response = client.post(url, headers=headers, json=payload)
        response.raise_for_status()
        return response.json()
