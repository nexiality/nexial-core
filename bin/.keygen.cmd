@echo off

set KEYGEN_HOME=
if EXIST "%JAVA_HOME%\bin\keytool.exe" (
	set KEYGEN_HOME="%JAVA_HOME%\bin"
) else if EXIST "%JRE_HOME%\bin\keytool.exe" (
	set KEYGEN_HOME="%JRE_HOME%\bin"
) else (
	echo !!! ERROR !!!
	echo Unable to find keytool executable under JAVA_HOME
	exit /b -1
)

echo.
del /f %USER_NEXIAL_KEYSTORE% 2>NUL
%KEYGEN_HOME%\keytool -genkey ^
	-keyalg RSA ^
	-validity 3650 ^
	-keystore %USER_NEXIAL_KEYSTORE% ^
	-storepass "nexialrocks" ^
	-keypass "nexialrocks" ^
	-alias "default" ^
	-dname "CN=127.0.0.1, OU=Nexial, O=Nexial, L=Nexial, S=Nexial, C=Nexiality"
exit /b %ERRORLEVEL%
