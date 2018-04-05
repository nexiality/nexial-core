@echo off

cd ..

gradle clean testJar installDist

xcopy /E support\nexial*.* build\install\nexial-core\support\

REM # generate the latest command listing
cd build\install\nexial-core\support\

nexial-command-generator.cmd
