@echo off

cd ..

gradle clean testJar installDist

xcopy /E support build\install\nexial-core\support\

cd build\install\nexial-core\support\

nexial-command-generator.cmd
