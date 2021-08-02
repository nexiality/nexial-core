@echo off
setlocal enableextensions
cd ..
REM generated nexial-lib-x.x.zip in build/distributions.
echo.
echo %prefix% starting lib generation process
call gradle clean generateNexialLib

if ERRORLEVEL 1 (
    echo.
    echo nexial-lib generation failed
    exit /b %ERRORLEVEL%
) else (
    echo nexial-lib generated successfully
)

