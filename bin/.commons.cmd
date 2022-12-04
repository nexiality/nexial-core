@echo off
    setlocal enableextensions enabledelayedexpansion

    rem Not to be directly called
    exit /b 9009


:init
    REM utilities to be invoked by other frontend scripts
    set PROJECT_BASE=%SystemDrive%\projects
    set NEXIAL_HOME=%~dp0..
    set NEXIAL_LIB=%NEXIAL_HOME%\lib
    set NEXIAL_CLASSES=%NEXIAL_HOME%\classes
    set USER_NEXIAL_HOME=%USERPROFILE%\.nexial
    set USER_NEXIAL_LIB=%USER_NEXIAL_HOME%\lib
    set USER_NEXIAL_JAR=%USER_NEXIAL_HOME%\jar
    set USER_NEXIAL_DLL=%USER_NEXIAL_HOME%\dll
    set USER_NEXIAL_INSTALL=%USER_NEXIAL_HOME%\install
    set USER_NEXIAL_KEYSTORE=%USER_NEXIAL_HOME%\nexial-keystore.jks

    set MSG_WARNING_HEADER==WARNING========================================================================
    set MSG_FOOTER=================================================================================
    set TMP_JAVA_VERSION=%TEMP%\java_version

    call :preset

    if exist "%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe" (
        set DEFAULT_CHROME_BIN="%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe"
    )
    if exist "%ProgramFiles%\Google\Chrome\Application\chrome.exe" (
        set DEFAULT_CHROME_BIN="%ProgramFiles%\Google\Chrome\Application\chrome.exe"
    )
    if exist "%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe" (
        set DEFAULT_CHROME_BIN="%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe"
    )

    if exist "%ProgramFiles%\Mozilla Firefox\firefox.exe" (
        set DEFAULT_FIREFOX_BIN="%ProgramFiles%\Mozilla Firefox\firefox.exe"
    )
    if exist "%ProgramFiles(x86)%\Mozilla Firefox\firefox.exe" (
        set DEFAULT_FIREFOX_BIN="%ProgramFiles(x86)%\Mozilla Firefox\firefox.exe"
    )

    REM android sdk
    if defined ANDROID_HOME (
        if exist "%ANDROID_HOME%\nul" (
            REM echo found ANDROID_HOME set to %ANDROID_HOME% 
        ) else (
	        set ANDROID_HOME=%USER_NEXIAL_HOME%\android\sdk
        )
    ) else (
        set ANDROID_HOME=%USER_NEXIAL_HOME%\android\sdk
    )
	if defined ANDROID_SDK_ROOT (
	    if exist "%ANDROID_SDK_ROOT%\nul" (
	        REM echo found ANDROID_SDK_ROOT set to %ANDROID_SDK_ROOT%
        ) else (
	        set ANDROID_SDK_ROOT=%USER_NEXIAL_HOME%\android\sdk
	    )
	) else (
        set ANDROID_SDK_ROOT=%USER_NEXIAL_HOME%\android\sdk
	)

    REM javaui/jubula
    if not exist %JUBULA_HOME%\nul ( set JUBULA_HOME="%ProgramFiles%\jubula_8.8.0.034")

    mkdir "%USERPROFILE%\tmp" 2> NUL

    REM setting Java runtime options and classpath
    set JAVA_OPT=%JAVA_OPT% -ea
    set JAVA_OPT=%JAVA_OPT% -Xss24m
    set JAVA_OPT=%JAVA_OPT% -Djava.io.tmpdir="%USERPROFILE%\tmp"
    set JAVA_OPT=%JAVA_OPT% -Dfile.encoding=UTF-8
    set JAVA_OPT=%JAVA_OPT% -Dnexial.home="%NEXIAL_HOME%"
    set JAVA_OPT=%JAVA_OPT% -Dwebdriver.winium.verbose=false
    set JAVA_OPT=%JAVA_OPT% -Dwebdriver.winium.silent=false
    REM set JAVA_OPT=%JAVA_OPT% -Dwebdriver.winium.logpath=%TEMP%\winium-service.log
    set JAVA_OPT=%JAVA_OPT% -Dorg.apache.poi.util.POILogger=org.apache.poi.util.NullLogger

    REM remove erroneous setup.jar in .nexial/lib
    if exist "%USER_NEXIAL_LIB%\setup.jar" ( del "%USER_NEXIAL_LIB%\setup.jar" 2> NUL )

    goto :eof


REM # Make sure prerequisite environment variables are set
:checkJava
    if EXIST %NEXIAL_JAVA_HOME_IMPORT% (
        call %NEXIAL_JAVA_HOME_IMPORT%
    )

    IF [%JAVA%] == [] (
        if "%JAVA_HOME%"=="" (
            if "%JRE_HOME%"=="" (
                echo %MSG_WARNING_HEADER%
                echo Neither the JAVA_HOME nor the JRE_HOME environment variable is defined.
                echo Nexial will use the JVM based on current PATH. Nexial requires Java 1.8
                echo or above to run, so this might not work...
                echo %MSG_FOOTER%
                echo.

                call :resolveJavaFromWhereCommand
                goto :check_exit
            ) else (
                if EXIST "%JRE_HOME%\bin\java.exe" (
                    set JAVA="%JRE_HOME%\bin\java.exe"
                ) else (
                    echo ERROR!!!
                    echo The JRE_HOME environment variable is not defined correctly.
                    echo Unable to find "%JRE_HOME%\bin\java.exe"
                    echo.
                    call :showJavaNotFoundError
                    call :optToInstallJava
                    goto :check_exit
                )
            )
        ) else (
            if EXIST "%JAVA_HOME%\bin\java.exe" (
                set JAVA="%JAVA_HOME%\bin\java.exe"
            ) else (
                echo ERROR!!!
                echo The JAVA_HOME environment variable is not defined correctly.
                echo Unable to find "%JAVA_HOME%\bin\java.exe"
                echo.
                call :showJavaNotFoundError
                call :optToInstallJava
                goto :check_exit
            )
        )
    )

    set JAVA_VERSION=
    set JAVA_SUPPORTS_MODULE=true
    set current_java=%JAVA%
    call :findJavaVersion %current_java%

    set /p installed_jdk_version=< %TMP_JAVA_VERSION%
    del %TMP_JAVA_VERSION%

    for /f "tokens=3" %%i in ("%installed_jdk_version%") do set installed_jdk_version=%%i
    set installed_jdk_version=%installed_jdk_version:"=%

    for /F "tokens=1 delims=." %%a in ("%installed_jdk_version%") do (
        set JAVA_VERSION=%%a
    )

    if %JAVA_VERSION% LSS %MINIMUM_JAVA_VERSION% (
        call :showJavaIncompatibleError
        call :optToInstallJava
        goto :check_exit
    )

    if "%JAVA_SUPPORTS_MODULE%"=="true" ( set JAVA_OPT=%JAVA_OPT% --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.text=ALL-UNNAMED --add-opens java.desktop/java.awt.font=ALL-UNNAMED)

    goto :eof


:check_exit
    REM TODO not sure if we need single quote here...
    for /f %%i in ('set NEXIAL_RC') do (
        if '%%i'=='NEXIAL_RC=3' (
            goto :exitCommons
        ) else (
            call :checkJava
        )
    )


:title
    echo.
    echo --------------------------------------------------------------------------------
    echo ^|                      nexial - automation for everyone!                      ^|
    echo --------------------------------------------------------------------------------
    echo [:: %~1 ::]
    echo --------------------------------------------------------------------------------
    goto :eof


:resolveEnv
    set NEXIAL_LIB=%NEXIAL_HOME%\lib
    set CLASSES_PATH=%NEXIAL_HOME%\classes
    if EXIST %NEXIAL_HOME%\version.txt (
        set /p NEXIAL_VERSION=<%NEXIAL_HOME%\version.txt
    )
    set datestr=%date% %time%

    echo ENVIRONMENT:
    echo   CURRENT TIME:     %datestr%
    echo   CURRENT USER:     %USERNAME%
    echo   CURRENT HOST:     %COMPUTERNAME%
    echo   JAVA:             %JAVA%
    echo   JAVA_VERSION:     %JAVA_VERSION%
    echo   NEXIAL_VERSION:   %NEXIAL_VERSION%
    echo   NEXIAL_HOME:      %NEXIAL_HOME%
    echo   NEXIAL_LIB:       %NEXIAL_LIB%
    echo   NEXIAL_CLASSES:   %NEXIAL_CLASSES%
    echo   PROJECT_BASE:     %PROJECT_BASE%
    if NOT "%PROJECT_HOME%"=="" (
        echo   PROJECT_HOME:     %PROJECT_HOME%
    )
    echo   USER_NEXIAL_HOME: %USER_NEXIAL_HOME%
    echo.
    goto :eof


:optToInstallJava
    %NEXIAL_BIN%.java_check_and_install.cmd


:findJavaVersion
    %NEXIAL_BIN%.java_check_and_install.cmd


:preset
    %NEXIAL_BIN%.java_check_and_install.cmd


:showJavaIncompatibleError
    %NEXIAL_BIN%.java_check_and_install.cmd


:showJavaNotFoundError
    %NEXIAL_BIN%.java_check_and_install.cmd


:resolveJavaFromWhereCommand
    %NEXIAL_BIN%.java_check_and_install.cmd


:exitCommons
    exit /b %NEXIAL_RC%