#!/usr/bin/env bash

# -----------------------------------------------------------------------------------------------
# Copy local file to connected Android device
# -----------------------------------------------------------------------------------------------

if [[ "$2" = "" ]]; then
    echo
    echo "ERROR: No source file or target folder specified!"
    echo "USAGE: $0 [source file] [target folder]"
    echo
    exit 254
fi

ANDROID_SDK_ROOT=~/.nexial/android/sdk
cd $ANDROID_SDK_ROOT/platform-tools || exit
./adb push "$1" "/storage/self/primary/$2"
exit $?
