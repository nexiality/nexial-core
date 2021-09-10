@echo off
setlocal enableextensions

REM -----------------------------------------------------------------------------------------------
REM Start Appium server from either Appium desktop (APPIUM_DESKTOP_HOME) or global node_module
REM Appium (APPIUM_HOME)
REM -----------------------------------------------------------------------------------------------

REM runtime options
REM https://appium.io/docs/en/writing-running-appium/server-args/
if not "%1"=="" ( set APPIUM_IP=%1) else ( set APPIUM_IP=127.0.0.1)
if "%APPIUM_LOG%"=="" ( set APPIUM_LOG=%TEMP%\appium.log)
set APPIUM_OPT=--allow-cors --session-override --log %APPIUM_LOG% --log-level info:info --log-timestamp --local-timezone --no-perms-check --debug-log-spacing --relaxed-security -a %APPIUM_IP% %APPIUM_OPT%

echo.
echo Usage: %0 [optional:listening IP]
echo Ctrl-C to stop
echo.

REM favors global appium node module over appium desktop installation
where /Q appium.exe
if "%ERRORLEVEL%"=="0" (
    echo Running appium server via appium.exe
    appium.exe %APPIUM_OPT%
    goto :end
)

REM can't find appium.exe, let's try appium desktop instead
if NOT "%APPIUM_DESKTOP_HOME%"=="" (
    if NOT EXIST %APPIUM_DESKTOP_HOME%\nul (
        echo ERROR!!! Invalid APPIUM_DESKTOP_HOME specified.
        echo Please be sure to set APPIUM_DESKTOP_HOME to the directory where Appium Desktop
        echo is installed.
        echo.
        echo.
        exit /b -1
        goto :end
    )
) else (
    set APPIUM_DESKTOP_HOME="%LOCALAPPDATA%\Programs\Appium"
    if NOT EXIST %APPIUM_DESKTOP_HOME%\nul (
        set APPIUM_DESKTOP_HOME="%ProgramFiles%\Appium"
        if NOT EXIST %APPIUM_DESKTOP_HOME%\nul (
            set APPIUM_DESKTOP_HOME="%ProgramFiles(x86)%\Appium"
            if NOT EXIST %APPIUM_DESKTOP_HOME%\nul (
                echo ERROR!!! Unable to resolve path to Appium Desktop. Perhaps it isn't installed?
                echo Please be sure to install Appium Desktop, and set its installation directory to
                echo the APPIUM_DESKTOP_HOME environment variable.
                echo.
                echo.
                exit /b -1
                goto :end
            )
        )
    )
)

set APPIUM_SCRIPT=%APPIUM_DESKTOP_HOME%\resources\app\node_modules\appium\build\lib\main.js
if NOT EXIST "%APPIUM_SCRIPT%" (
    echo ERROR!!! Invalid Appium script at %APPIUM_SCRIPT%.
    echo Please be sure to set APPIUM_DESKTOP_HOME to the directory where Appium Desktop
    echo is installed.
    echo.
    echo.
    exit /b -1
    goto :end
)

echo Running appium server script from %APPIUM_SCRIPT%
node %APPIUM_SCRIPT% %APPIUM_OPT%
goto :end

:end
endlocal
