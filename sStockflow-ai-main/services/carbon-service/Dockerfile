FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY stockflow_carbon ./stockflow_carbon

CMD ["sh", "-c", "uvicorn stockflow_carbon.main:app --host 0.0.0.0 --port ${PORT:-8080}"]
