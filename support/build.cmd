@echo off

cd ..


if "%1"=="" goto build_no_test
gradle clean test testJar installDist
goto post_build

:build_no_test
gradle clean testJar installDist

:post_build
xcopy /E support\nexial*.* build\install\nexial-core\support\

REM # generate the latest command listing
cd build\install\nexial-core\support\

nexial-command-generator.cmd

