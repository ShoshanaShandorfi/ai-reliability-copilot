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
    echo Skipping empty ID in %%P
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


REM ==========================================
REM Child process: POST one file and save ID
REM ==========================================
:post

set IDX=%~2
set FILE=%~3

curl -s -X POST http://localhost:8080/logs/file -F "file=@%BASE_PATH%\%FILE%" > post_%IDX%.txt

exit /b


REM ==========================================
REM Child process: Poll result until not PROCESSING
REM ==========================================
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