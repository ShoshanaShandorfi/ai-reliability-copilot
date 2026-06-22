# AI Reliability Copilot — Full Setup & Usage Tutorial

This file explains how to run **AI Reliability Copilot** locally from scratch.

It includes:

- PostgreSQL setup
- Redis setup
- Ollama setup
- Docker / Docker Compose setup
- Spring Boot application configuration
- API URLs and how to use them
- Demo log files
- Concurrency test script
- Troubleshooting commands

---

## 1. Project Overview

**AI Reliability Copilot** is an async AI-powered log analysis system.

High-level flow:

```text
Client / curl
  ↓
Spring Boot API
  ↓
PostgreSQL cache by content hash
  ↓
Redis Stream
  ↓
Consumer
  ↓
Ollama local LLM
  ↓
PostgreSQL result storage
  ↓
GET analysis result by id
```

Main components:

| Component | Purpose |
|---|---|
| Spring Boot | REST API and orchestration |
| PostgreSQL | Stores jobs, statuses, cached results |
| Redis Streams | Async processing queue |
| Ollama | Local LLM for log analysis |
| Docker | Runs infrastructure locally |

---

## 2. Prerequisites

Install the following:

- Java 17+
- Maven
- Docker Desktop
- Git
- Ollama
- Optional: DBeaver for DB inspection

Verify:

```bash
java -version
mvn -version
docker --version
git --version
```

---

## 3. Clone the Project

```bash
git clone <YOUR_REPOSITORY_URL>
cd "AI Reliability Copilot"
```

Example local path used during development:

```text
C:\git\AI Reliability Copilot
```

---

## 4. Docker Compose Setup

Create a single `docker-compose.yml` file in the project root:

```yaml
version: "3.8"

services:
  postgres:
    image: postgres:15
    container_name: ai-postgres
    environment:
      POSTGRES_DB: logsdb
      POSTGRES_USER: user
      POSTGRES_PASSWORD: password
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7
    container_name: ai-redis
    ports:
      - "6379:6379"

volumes:
  postgres_data:
```

Start infrastructure:

```bash
docker compose up -d
```

Verify containers:

```bash
docker ps
```

Expected containers:

```text
ai-postgres
ai-redis
```

---

## 5. PostgreSQL Setup

### Connect using Docker

```bash
docker exec -it ai-postgres psql -U user -d logsdb
```

Useful commands inside `psql`:

```sql
\dt
select * from log_analysis_jobs;
select count(*), status from log_analysis_jobs group by status;
```

### Drop table during development

If you changed the entity structure and want Hibernate to recreate the table:

```sql
DROP TABLE log_analysis_jobs;
```

Then restart the Spring Boot application.

### DBeaver connection

Use these settings:

```text
Host: localhost
Port: 5432
Database: logsdb
User: user
Password: password
Schema: public
```

Important: connect to `logsdb`, not the default `postgres` database.

---

## 6. Redis Setup

### Open Redis CLI

```bash
docker exec -it ai-redis redis-cli
```

### Inspect the stream

The application uses:

```text
logs_stream
```

Useful Redis commands:

```redis
XINFO STREAM logs_stream
XRANGE logs_stream - + COUNT 10
XINFO GROUPS logs_stream
XPENDING logs_stream group1
XINFO CONSUMERS logs_stream group1
```

### Expected healthy state

A healthy system should usually show:

```text
XPENDING logs_stream group1
1) (integer) 0
```

If pending messages exist, inspect them:

```redis
XPENDING logs_stream group1
XRANGE logs_stream <message-id> <message-id>
```

To acknowledge an old stuck message manually:

```redis
XACK logs_stream group1 <message-id>
```

---

## 7. Ollama Setup

Install Ollama from:

```text
https://ollama.com
```

Start Ollama:

```bash
ollama serve
```

Pull a model:

```bash
ollama pull llama3.2
```

Or use another local model:

```bash
ollama pull mistral
ollama pull qwen2.5:7b
```

Verify Ollama is running:

```bash
curl http://localhost:11434/api/tags
```

Expected: JSON response with installed models.

---

## 8. Spring Boot Configuration

Example `application.properties`:

```properties
spring.application.name=ai-reliability-copilot

spring.datasource.url=jdbc:postgresql://localhost:5432/logsdb
spring.datasource.username=user
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

spring.data.redis.host=localhost
spring.data.redis.port=6379

ollama.url=http://localhost:11434/api/chat
ollama.model=llama3.2
```

During development, if you want Hibernate to recreate tables:

```properties
spring.jpa.hibernate.ddl-auto=create
```

Important: do not keep `create` permanently unless you intentionally want tables deleted on every startup.

---

## 9. Run the Application

From the project root:

```bash
mvn spring-boot:run
```

Or build and run:

```bash
mvn clean package
java -jar target/*.jar
```

The API should run on:

```text
http://localhost:8080
```

---

## 10. Demo Logs Location

Place demo logs here:

```text
C:\git\AI Reliability Copilot\src\main\resources\demo-logs
```

Current demo files:

```text
big-log.txt
big-log-2.txt
big-log-3.txt
log1.txt
```

---

## 11. API Usage

### 11.1 Upload log file for analysis

Endpoint:

```text
POST http://localhost:8080/logs/file
```

Example:

```bash
curl -X POST http://localhost:8080/logs/file -F "file=@C:\git\AI Reliability Copilot\src\main\resources\demo-logs\big-log.txt"
```

Expected response:

```text
<job-id>
```

Example:

```text
463287e4-5448-4479-af29-4a7edc0af68c
```

### 11.2 Get analysis result

Endpoint:

```text
GET http://localhost:8080/logs/analysis/{id}
```

Example:

```bash
curl http://localhost:8080/logs/analysis/463287e4-5448-4479-af29-4a7edc0af68c
```

Possible response while processing:

```json
{
  "status": "PROCESSING"
}
```

Possible final response:

```json
{
  "rootCause": "Gateway timeout",
  "explanation": "Gateway timed out while calling PaymentService, suggesting a dependency issue.",
  "suggestions": [
    "Increase the timeout value for gateway connections to prevent similar timeouts.",
    "Investigate and resolve any issues with the PaymentService endpoint that is causing the timeout.",
    "Consider implementing circuit breaking or retry mechanisms to handle temporary gateway unavailability."
  ],
  "severity": "HIGH",
  "confidence": "MEDIUM",
  "status": "DONE"
}
```

---

## 12. Important Behavior: Cache by Content Hash

The system should avoid calling AI repeatedly for the same log content.

Expected behavior:

```text
Same file content
  ↓
Same content hash
  ↓
Return existing job/result from PostgreSQL
  ↓
Do not call Ollama again
```

This prevents:

- Duplicate AI calls
- Unnecessary load
- Non-deterministic results for the same log
- Long processing times under repeated uploads

Recommended implementation detail:

```java
private static final String PROMPT_VERSION = "v1";
String contentHash = hash(PROMPT_VERSION + ":" + rawContent);
```

When changing the prompt meaningfully, increment:

```java
PROMPT_VERSION = "v2";
```

This prevents old cached results from being reused after prompt changes.

---

## 13. Concurrency Test Script

Create this file in the project root:

```text
run-concurrency-test.cmd
```

```bat
@echo off
setlocal enabledelayedexpansion

set BASE_PATH=C:\git\AI Reliability Copilot\src\main\resources\demo-logs
set FILES=big-log.txt big-log-2.txt big-log-3.txt log1.txt
set REPEATS=5
set MAX_POLLS=120

if "%~1"=="post" goto post
if "%~1"=="get" goto get

echo === CLEANUP ===
del post_*.txt 2>nul
del result_*.txt 2>nul
del done_*.txt 2>nul

echo === STEP 1: FIRE MANY POSTS IN PARALLEL ===

set INDEX=0

for %%F in (%FILES%) do (
    for /L %%i in (1,1,%REPEATS%) do (
        set /a INDEX+=1
        start "" /b cmd /c call "%~f0" post !INDEX! "%%F"
    )
)

set EXPECTED=%INDEX%

echo Fired %EXPECTED% POST requests.

echo === STEP 2: WAIT FOR POST IDS ===

:wait_posts
set COUNT=0

for /f %%C in ('dir /b post_*.txt 2^>nul ^| find /c /v ""') do (
    set COUNT=%%C
)

echo POST results: !COUNT!/%EXPECTED%

if not "!COUNT!"=="%EXPECTED%" (
    timeout /t 1 >nul
    goto wait_posts
)

echo === STEP 3: START GET POLLING IN PARALLEL ===

for %%P in (post_*.txt) do (
    set IDX=%%~nP
    set IDX=!IDX:post_=!

    set /p ID=<%%P

    if "!ID!"=="" (
        echo Skipping empty ID from %%P
    ) else (
        start "" /b cmd /c call "%~f0" get !IDX! !ID!
    )
)

echo === STEP 4: WAIT FOR DONE MARKERS ===

:wait_done
set DONE_COUNT=0

for /f %%C in ('dir /b done_*.txt 2^>nul ^| find /c /v ""') do (
    set DONE_COUNT=%%C
)

echo DONE results: !DONE_COUNT!/%EXPECTED%

if not "!DONE_COUNT!"=="%EXPECTED%" (
    timeout /t 1 >nul
    goto wait_done
)

echo === STEP 5: PRINT ALL RESULTS ===

for %%f in (result_*.txt) do (
    echo ------------------------------
    echo FILE: %%f
    type %%f
    echo.
)

echo === STEP 6: CLEANUP TEMP FILES ===

del post_*.txt 2>nul
del result_*.txt 2>nul
del done_*.txt 2>nul

echo Cleanup completed.
echo === DONE ===

pause
exit /b

:post
set IDX=%~2
set FILE=%~3

curl -s -X POST http://localhost:8080/logs/file -F "file=@%BASE_PATH%\%FILE%" > post_%IDX%.txt

exit /b

:get
set IDX=%~2
set ID=%~3
set TRY=0

:poll
set /a TRY+=1

curl -s http://localhost:8080/logs/analysis/%ID% > result_%IDX%.txt

find "PROCESSING" result_%IDX%.txt >nul

if not errorlevel 1 (
    if !TRY! GEQ %MAX_POLLS% (
        echo {"status":"TIMEOUT","id":"%ID%"} > result_%IDX%.txt
        echo done > done_%IDX%.txt
        exit /b
    )

    timeout /t 1 >nul
    goto poll
)

echo done > done_%IDX%.txt

exit /b
```

Run it from CMD:

```cmd
run-concurrency-test.cmd
```

Or from PowerShell:

```powershell
cmd /c run-concurrency-test.cmd
```

Expected outcome:

```text
POST results: 20/20
DONE results: 20/20
```

---

## 14. Expected Valid AI Output Contract

Valid output must look like this:

```json
{
  "rootCause": "Gateway timeout",
  "explanation": "Gateway timed out while calling PaymentService, suggesting a dependency issue.",
  "suggestions": [
    "Increase the timeout value for gateway connections to prevent similar timeouts.",
    "Investigate and resolve any issues with the PaymentService endpoint that is causing the timeout.",
    "Consider implementing circuit breaking or retry mechanisms to handle temporary gateway unavailability."
  ],
  "severity": "HIGH",
  "confidence": "MEDIUM",
  "status": "DONE"
}
```

Invalid values to avoid:

```json
{
  "severity": "LOW|MEDIUM|HIGH",
  "confidence": "LOW|MEDIUM|HIGH"
}
```

---

## 15. Troubleshooting

### 15.1 API returns 404 for `/logs/analysis/`

Cause:

```text
The request is missing the id.
```

Wrong:

```bash
curl http://localhost:8080/logs/analysis/
```

Correct:

```bash
curl http://localhost:8080/logs/analysis/<id>
```

---

### 15.2 Result stays PROCESSING

Check Redis:

```bash
docker exec -it ai-redis redis-cli
XPENDING logs_stream group1
```

Check DB:

```sql
select count(*), status
from log_analysis_jobs
group by status;
```

Check Ollama:

```bash
curl http://localhost:11434/api/tags
```

---

### 15.3 `Unable to access lob stream`

Cause: using `@Lob` on String fields with PostgreSQL/Hibernate.

Fix:

Use:

```java
@Column(columnDefinition = "TEXT")
private String preparedContent;
```

Do not use:

```java
@Lob
private String preparedContent;
```

Then recreate the table:

```sql
DROP TABLE log_analysis_jobs;
```

---

### 15.4 Docker exec syntax error

Wrong:

```bash
docker exec f90 -it psql -U user -d logsdb
```

Correct:

```bash
docker exec -it ai-postgres psql -U user -d logsdb
```

---

### 15.5 PowerShell curl issue

In PowerShell, `curl` may map to `Invoke-WebRequest`.

Use:

```powershell
curl.exe http://localhost:8080/logs/analysis/<id>
```

Or run CMD scripts with:

```powershell
cmd /c run-concurrency-test.cmd
```

---

## 16. Useful Manual Test Commands

Upload file:

```bash
curl -X POST http://localhost:8080/logs/file -F "file=@C:\git\AI Reliability Copilot\src\main\resources\demo-logs\big-log.txt"
```

Get result:

```bash
curl http://localhost:8080/logs/analysis/<id>
```

Inspect DB cache:

```sql
select file_name, content_hash, count(*)
from log_analysis_jobs
group by file_name, content_hash
order by file_name;
```

Clear all jobs during local development:

```sql
delete from log_analysis_jobs;
```

Inspect Redis stream:

```redis
XINFO STREAM logs_stream
XRANGE logs_stream - + COUNT 10
XINFO GROUPS logs_stream
XPENDING logs_stream group1
```

---

## 17. Recommended Development Workflow

When changing the prompt:

1. Increment `PROMPT_VERSION`
2. Restart the application
3. Clear old DB rows if needed
4. Run:

```cmd
run-concurrency-test.cmd
```

5. Verify:

```text
DONE results: 20/20
No TIMEOUT
No LOW|MEDIUM|HIGH
Same log file returns consistent results
```

---

## 18. Final Notes

This system is designed as a local distributed AI reliability pipeline.

The important production ideas demonstrated here are:

- Async processing
- Redis Stream queue
- Consumer group handling
- PostgreSQL persistence
- Content-hash-based deduplication
- Prompt-driven AI analysis
- Local LLM execution with Ollama
- Concurrency testing

