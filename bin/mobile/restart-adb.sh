#!/usr/bin/env bash

# --------------------------------------------------------------------------------------------------
# Restart Android SDK's ADB to disconnect any previous or current device connections and reconnect
# to any active connections. Run this batch file when the connected device (real or emulator) is
# now registering with adb.
#
# For more details and options on ADB, check out
#     https://developer.android.com/studio/command-line/adb#devicestatus
# --------------------------------------------------------------------------------------------------

export ANDROID_SDK_ROOT=~/.nexial/android/sdk
cd $ANDROID_SDK_ROOT/platform-tools || exit
./adb kill-server
./adb start-server
./adb devices -l

read -p "Press [Enter] key to end this script..."
