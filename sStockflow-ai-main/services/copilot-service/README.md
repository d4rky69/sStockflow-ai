# StockFlow Copilot Host

This read-only FastAPI service connects Gemini to the authorised StockFlow MCP servers.

1. Copy `.env.example` to `.env` and set `GEMINI_API_KEY` on the server only.
2. Start the Core API, Data MCP and Intelligence MCP services.
3. Run `run-copilot-windows.cmd` from the repository root.
4. Start Angular; its `/api/v1/copilot` requests are proxied to `127.0.0.1:8300`.

Keep `STOCKFLOW_ENABLE_ACTIONS=false` during development. In production, set
`AUTH_DISABLED_FOR_LOCAL=false`, configure Supabase JWT verification, and expose
the Copilot Host through an authenticated reverse proxy or service URL.
