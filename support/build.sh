#!/usr/bin/env bash

cd ..

gradle clean testJar installDist

cp -Rf support build/install/nexial-core/

cd build/install/nexial-core/support
chmod -fR 755 *.sh
./nexial-command-generator.sh
