# Windows Run Guide — Sprint 1

## Prerequisites

```cmd
node --version
npm --version
java -version
python --version
uv --version
mvn --version
```

The Windows scripts can also detect Maven automatically when it is installed below either of these folders:

```text
%USERPROFILE%\Tools\apache-maven-*\bin\mvn.cmd
C:\Tools\apache-maven-*\bin\mvn.cmd
```

## Recommended first run: backend only

From the repository root:

```cmd
run-core-api-windows.cmd
```

The script runs the backend tests and starts Spring Boot only when they pass.

Verify:

```text
http://localhost:8080/actuator/health
http://localhost:8080/api/v1/dashboard/overview
```

Stop the backend with `Ctrl+C` before using the complete launcher.

## Run the complete platform

From the repository root:

```cmd
RUN_ALL_WINDOWS.cmd
```

The launcher validates the installed tools, generates and validates synthetic data, and opens separate Command Prompt windows for:

- Kotlin Core API
- Angular UI
- Forecasting service
- Optimisation service
- MCP data server
- MCP intelligence server
- MCP action server

## Manual commands

### Generate and validate sample data

Run from the repository root:

```cmd
python scripts\generate_synthetic_data.py --config data\generator_config.yaml --output data\generated
python scripts\validate_synthetic_data.py --dataset data\generated
```

### Angular

```cmd
cd apps\stockflow-web
npm install
npm start
```

Open `http://localhost:4200`.

### Kotlin API

```cmd
cd services\stockflow-core-api
mvn clean test
mvn spring-boot:run
```

### Forecasting

```cmd
cd services\forecasting-service
uv sync
uv run uvicorn stockflow_forecasting.main:app --port 8101
```

### Optimisation

```cmd
cd services\optimisation-service
uv sync
uv run uvicorn stockflow_optimisation.main:app --port 8102
```

### MCP data server

```cmd
cd mcp
uv sync
uv run python -m stockflow_mcp.data_server
```

Do not test `/mcp` by opening it as a normal browser page. Use MCP Inspector:

```cmd
npx -y @modelcontextprotocol/inspector
```

The Kotlin module targets Java 17 bytecode so it can run on supported newer JDK runtimes as well.
