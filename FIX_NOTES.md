# StockFlow AI Sprint 1 — Backend Fix Notes

## Corrected issues

1. Spring Boot 4 uses Jackson 3 by default. The previous backend injected a Jackson 2 `com.fasterxml.jackson.databind.ObjectMapper`, for which Spring Boot 4 did not create a bean.
2. The dashboard fixture is now returned as an explicit `application/json` response without requiring a Jackson 2 mapper.
3. The obsolete direct Jackson 2 Kotlin-module dependency was removed.
4. Windows launchers now detect Maven from PATH, `%USERPROFILE%\Tools\apache-maven-*`, or `C:\Tools\apache-maven-*`.
5. Java/Kotlin bytecode remains targeted to Java 17 for compatibility with Java 17, 21, and 25 runtimes.

## Recommended first run

```cmd
run-core-api-windows.cmd
```

After the backend reports that it has started, verify:

- `http://localhost:8080/actuator/health`
- `http://localhost:8080/api/v1/dashboard/overview`

Then stop it with Ctrl+C and launch all components:

```cmd
RUN_ALL_WINDOWS.cmd
```
