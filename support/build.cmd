@echo off
setlocal enableextensions
set prefix=[nexial-core]
cd ..

:build
    echo.

    if "%1"=="test" (
        echo %prefix% starting build/test process
        call gradle clean test testJar installDist
    ) else (
        echo %prefix% starting build process
        call gradle clean testJar installDist
    )

    if ERRORLEVEL 1 (
        echo.
        echo %prefix% build failed
        exit /b %ERRORLEVEL%
    ) else (
        echo.
        echo %prefix% build complete
        goto post_build
    )

:post_build
    echo.
    echo %prefix% post-build start

    xcopy /E support\nexial*.* build\install\nexial-core\support\

    REM # generate the latest command listing
    del build\install\nexial-core\lib\nexial-json.jar
    cd build\install\nexial-core\support\
    call nexial-command-generator.cmd

    cd ..\bin
    call nexial-script-update.cmd -v -t ..\template
    call nexial-script-update.cmd -v -t ..\..\..\..\template

:end
