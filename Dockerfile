FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app/services/copilot-service:/app/mcp

WORKDIR /app

COPY mcp /app/mcp
COPY services/copilot-service /app/services/copilot-service

RUN pip install --no-cache-dir /app/mcp /app/services/copilot-service

CMD ["python", "/app/services/copilot-service/cloud_entrypoint.py"]
