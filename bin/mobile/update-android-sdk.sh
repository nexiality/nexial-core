#!/usr/bin/env bash

# -----------------------------------------------------------------------------------------------
# Update installed Android SDK found in $ANDROID_HOME
# -----------------------------------------------------------------------------------------------

ANDROID_SDK_ROOT=~/.nexial/android/sdk

cd ${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin || exit
./sdkmanager --update
