#!/usr/bin/env bash

cd ..

if [[ "$1" = "-t" ]]; then
	gradle clean test testJar installDist
else
	gradle clean testJar installDist
fi

build_ret=$?

if [[ ${build_ret} != 0 ]] ; then
    echo gradle build failed!
    exit ${build_ret}
fi

mkdir -p build/install/nexial-core/support
cp -Rvf support/nexial*.* build/install/nexial-core/support

# generate the latest command listing
cd build/install/nexial-core/support
chmod -fR 755 *.sh
./nexial-command-generator.sh

build_ret=$?

if [[ ${build_ret} != 0 ]] ; then
    echo command generator failed!
    exit ${build_ret}
fi

cd ..
rm -frv support

cd bin
./nexial-script-update.sh -v -t ../template

build_ret=$?

if [[ ${build_ret} != 0 ]] ; then
    echo script update failed!
    exit ${build_ret}
fi
