#!/usr/bin/env bash

cd ..

if [[ "$1" = "-t" ]]; then
	gradle clean test testJar installDist
else
	gradle clean testJar installDist
fi

build_ret=$?

if [[ ${build_ret} = 0 ]] ; then
	mkdir -p build/install/nexial-core/support
	cp -Rvf support/nexial*.* build/install/nexial-core/support

	# generate the latest command listing
	cd build/install/nexial-core/support
	chmod -fR 755 *.sh
	./nexial-command-generator.sh

	cd ..
	rm -frv support

	cd bin
	./nexial-script-update.sh -v -t ../template
else
	echo gradle build failed!
fi
