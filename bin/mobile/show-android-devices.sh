#!/usr/bin/env bash

# --------------------------------------------------------------------------------------------------
# Show android devices that are currently connected to this computer
#
# For more details and options on ADB, check out
# 	https://developer.android.com/studio/command-line/adb#devicestatus
# --------------------------------------------------------------------------------------------------

export ANDROID_SDK_ROOT=~/.nexial/android/sdk
cd $ANDROID_SDK_ROOT/platform-tools || exit
./adb devices -l

read -p "Press [Enter] key to end this script..."
