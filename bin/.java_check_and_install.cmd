@echo off
setlocal enableextensions

rem Not to be directly called
exit /b 9009


:optToInstallJava
    echo.
    echo Select one of the following options:
    echo.
    echo 1. Specify the location of Java that is version %MINIMUM_JAVA_VERSION% or higher
    echo 2. Let Nexial install a compatible Java for you
    echo 3. Exit
    set choice=
    set /p choice=Enter your selection (1, 2, or 3) and press Enter: 
    if not [%choice%]==[] set choice=%choice:~0,1%
    
    if [%choice%]==[1] (
        call :promptForCustomJdkLocation
        goto :eof
    )

    if [%choice%]==[2] (
        goto :tryDownloadingJava
        goto :eof
    )

    if [%choice%]==[3] (
        echo.
        echo Please install Java %MINIMUM_JAVA_VERSION% or higher manually for Nexial to work.
        echo Thanks for trying Nexial!
        echo.
        set NEXIAL_RC=3
        goto :eof
    )

    echo.
    echo Error: Invalid selection '%choice%'. Please enter your selection (1, 2, or 3) and press Enter.
    goto :optToInstallJava  
    goto :eof


:preset
    REM # utilities to be invoked by other frontend scripts
    set MINIMUM_JAVA_VERSION=17
    set NEXIAL_JAVA_HOME_IMPORT=%USER_NEXIAL_HOME%\.java_home.cmd
    set NEXIAL_JDK_DOWNLOAD_VERSION=17
    set NEXIAL_JAVA_HOME=%USER_NEXIAL_HOME%\jdk-%NEXIAL_JDK_DOWNLOAD_VERSION%
    set JDK_DOWNLOAD_URL=https://download.oracle.com/java/%NEXIAL_JDK_DOWNLOAD_VERSION%/latest/jdk-%NEXIAL_JDK_DOWNLOAD_VERSION%_windows-x64_bin.zip

    set MSG_ERR_HEADER===ERROR=========================================================================
    set MSG_SUCCESS_HEADER===SUCCESS========================================================================
    set MSG_FOOTER=================================================================================
    set MSG_MIN_JAVA=Nexial requires Java %MINIMUM_JAVA_VERSION% or higher to work.
    set MSG_SPECIFY_JAVA=If you have installed Java %MINIMUM_JAVA_VERSION% or higher,
    set MSG_SPECIFY_JAVA2=please exit and update the JAVA_HOME environment variable accordingly.
    goto :eof


:cacheJavaPath
    echo set JAVA=%1> %NEXIAL_JAVA_HOME_IMPORT%
    call %NEXIAL_JAVA_HOME_IMPORT%
    goto :eof


:showJavaIncompatibleError
    echo.
    echo %MSG_ERR_HEADER%
    echo %MSG_MIN_JAVA%
    echo The detected Java version is %installed_jdk_version%.
    echo %MSG_SPECIFY_JAVA%
    echo %MSG_SPECIFY_JAVA2%
    echo %MSG_FOOTER%
    echo.
    goto :eof


:showJavaNotFoundError
    echo.
    echo %MSG_ERR_HEADER%
    echo %MSG_MIN_JAVA%
    echo No Java is detected on the current system.
    echo %MSG_SPECIFY_JAVA%
    echo %MSG_SPECIFY_JAVA2%
    echo %MSG_FOOTER%
    echo.
    goto :eof


:findJavaVersion
    if [%current_java%]==[''] (
        echo 0 > %TMP_JAVA_VERSION%
        goto :eof
    ) else (
        %current_java% -version > %TMP_JAVA_VERSION% 2>&1
    )
    goto :eof


:tryDownloadingJava
    echo.
    echo Downloding JDK from %JDK_DOWNLOAD_URL%
    echo.
    curl.exe --output %TEMP%\jdk.zip "%JDK_DOWNLOAD_URL%"
    if not exist %NEXIAL_JAVA_HOME% mkdir %NEXIAL_JAVA_HOME%
    tar.exe -xf %TEMP%\jdk.zip -C %NEXIAL_JAVA_HOME% --strip-components=1

    set current_java=%NEXIAL_JAVA_HOME%\bin\java.exe
    call :findJavaVersion %current_java%

    set /p installed_jdk_version=< %TMP_JAVA_VERSION%
    del %TMP_JAVA_VERSION%

    for /f "tokens=3" %%i in ("%installed_jdk_version%") do set installed_jdk_version=%%i
    set installed_jdk_version=%installed_jdk_version:"=%

    echo.
    echo %MSG_SUCCESS_HEADER%
    echo Nexial installed the Java '%installed_jdk_version%' successfully.
    echo %MSG_FOOTER%

    call :cacheJavaPath %NEXIAL_JAVA_HOME%\bin\java.exe
    goto :eof


:promptForCustomJdkLocation
    echo.
    set USER_TYPED_JAVA_HOME=
    set /p USER_TYPED_JAVA_HOME=Type your custom JAVA_HOME location:

    set USER_PROVIDED_JAVA="%USER_TYPED_JAVA_HOME%\bin\java.exe"

    if EXIST %USER_PROVIDED_JAVA% (
        set JAVA=%USER_PROVIDED_JAVA%
        call :cacheJavaPath %USER_PROVIDED_JAVA%
    ) else (
        echo Error: No valid java executable binary found at %USER_PROVIDED_JAVA%
        echo Please provide a valid JAVA_HOME location (or use 'CTRL + C' to exit^)
        call :promptForCustomJdkLocation
    )
    goto :eof


:resolveJavaFromWhereCommand
    set JAVA=
    where java >nul 2>&1
    IF %ERRORLEVEL% EQU 0 (
        for /F "delims=" %%x in ('where java') do (
            if [%JAVA%]==[] (
                set JAVA="%%x"
                call :cacheJavaPath %JAVA%
                goto :eof
            )
        )
    )

    if [%JAVA%]==[] (
        call :showJavaNotFoundError
        goto :optToInstallJava
    )
    goto :eof