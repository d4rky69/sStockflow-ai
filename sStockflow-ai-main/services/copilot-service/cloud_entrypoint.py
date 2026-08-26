import os
import socket
import subprocess
import sys
import time
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SOURCE_PATHS = [
    str(REPO_ROOT / "services" / "copilot-service"),
    str(REPO_ROOT / "mcp"),
]
os.environ["PYTHONPATH"] = os.pathsep.join(
    [*SOURCE_PATHS, os.environ.get("PYTHONPATH", "")]
)
for source_path in reversed(SOURCE_PATHS):
    if source_path not in sys.path:
        sys.path.insert(0, source_path)

import uvicorn


def wait_for_port(port: int, timeout_seconds: int = 30) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        with socket.socket() as connection:
            connection.settimeout(0.5)
            if connection.connect_ex(("127.0.0.1", port)) == 0:
                return
        time.sleep(0.25)
    raise RuntimeError(f"Local MCP server did not start on port {port}")


def main() -> None:
    child_processes = [
        subprocess.Popen([sys.executable, "-m", "stockflow_mcp.data_server"]),
        subprocess.Popen([sys.executable, "-m", "stockflow_mcp.intelligence_server"]),
    ]
    if os.getenv("STOCKFLOW_ENABLE_ACTIONS", "false").lower() == "true":
        child_processes.append(subprocess.Popen([sys.executable, "-m", "stockflow_mcp.action_server"]))
    try:
        wait_for_port(8201)
        wait_for_port(8202)
        if os.getenv("STOCKFLOW_ENABLE_ACTIONS", "false").lower() == "true":
            wait_for_port(8203)
        uvicorn.run(
            "stockflow_copilot.main:app",
            host="0.0.0.0",
            port=int(os.getenv("PORT", "8080")),
            proxy_headers=True,
        )
    finally:
        for process in child_processes:
            process.terminate()
        for process in child_processes:
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()


if __name__ == "__main__":
    main()
