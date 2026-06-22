@echo off
setlocal enabledelayedexpansion

set BASE_PATH=C:\git\AI Reliability Copilot\src\main\resources\demo-logs
set FILES=big-log.txt big-log-2.txt big-log-3.txt log1.txt

echo === STEP 1: POST + ASYNC PROCESS ===

for %%F in (%FILES%) do (
    for /L %%i in (1,1,5) do (

        echo ------------------------------
        echo Processing file: %%F

        REM --- POST (get ID directly)
        for /f "delims=" %%r in ('
            curl -s -X POST http://localhost:8080/logs/file -F "file=@\"%BASE_PATH%\%%F\""
        ') do (

            set LINE=%%r

            echo RAW response: !LINE!

            REM --- extract ID safely
            set LINE=!LINE:"id":"=!
            set LINE=!LINE:"=!

            set ID=!LINE!

            echo Parsed ID: !ID!

            REM --- polling until DONE
            call :poll !ID!

        )
    )
)

echo === DONE ===
pause
exit /b

REM ==========================================
REM ✅ polling function (critical part)
REM ==========================================
:poll

set ID=%1

:retry

for /f "delims=" %%r in ('curl -s http://localhost:8080/logs/analysis/%ID%') do (
    set RESULT=%%r
)

echo [%ID%] !RESULT!

echo !RESULT! | find "PROCESSING" >nul
if %errorlevel%==0 (
    timeout /t 1 >nul
    goto retry
)

echo [%ID%] ✅ DONE
echo.

exit /b
