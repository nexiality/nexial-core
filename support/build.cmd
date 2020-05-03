@echo off

setlocal enableextensions

cd ..

if "%1"=="" goto build_no_test

call gradle clean test testJar installDist
echo "test/build complete"
goto post_build

:build_no_test
call gradle clean testJar installDist
echo "build complete"

:post_build
echo "post-build start"
xcopy /E support\nexial*.* build\install\nexial-core\support\

REM # generate the latest command listing
cd build\install\nexial-core\support\
call nexial-command-generator.cmd

cd ..\bin
call nexial-script-update.cmd -v -t ..\template
call nexial-script-update.cmd -v -t ..\..\..\..\template

:end
