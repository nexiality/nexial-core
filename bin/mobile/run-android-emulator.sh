#!/usr/bin/env bash

# -----------------------------------------------------------------------------------------------
# Start Android Emulator using %1 as the emulator ID
#
# One can customize emulator behavior by setting the following environment variables prior to
# invoking this script:
#
#     EMULATOR_TIMEZONE   - instruct Android emulator to use the specified timezone instead of
#                           system default
#     EMULATOR_MEMORY     - instruct Android emulator to use different RAM setting instead of
#                           system default of 1536 MB
#     EMULATOR_HEADLESS   - true|false to instruct Android emulator to run in desktop (default)
#                           or headless mode.
#     EMULATOR_FRONT_CAM  - emulated|webcam0|webcam1|...
#                           instruct Android emulator to use either emulated image or host camera
#     EMULATOR_BACK_CAM   - emulated|webcam0|webcam1|...
#                           instruct Android emulator to use either emulated image or host camera
#     EMULATOR_PHONE      - 10-digit; instruct Android emulator to use specified phone number
#
# For example,
#
#     EMULATOR_TIMEZONE=America/Los_Angeles
#     EMULATOR_MEMORY=4096
#     ./run-android-emulator.sh Pixel_04a
#
# The above will instruct the Android Emulator to use Los Angeles as its timezone (i.e. PST)
# and allocate 4 GB of RAM for the specified emulator.
#
# For more details and options on Android emulator, check out
#     https://developer.android.com/studio/run/emulator-commandline
# -----------------------------------------------------------------------------------------------

if [[ "$1" = "" ]]; then
    echo
    echo "ERROR: No emulator id (avd id) specified!"
    echo "USAGE: $0 [emulator id]"
    echo
    echo "Additional environment variables:"
		echo "EMULATOR_TIMEZONE   - instruct Android emulator to use the specified timezone instead of system"
		echo "                      default. For example: America/Los_Angeles"
		echo "EMULATOR_MEMORY     - instruct Android emulator to use different RAM setting instead of the system"
		echo "                      default of 1536 MB. For example: 4096 (for 4 GB)"
		echo "EMULATOR_HEADLESS   - true|false to instruct Android emulator to run in desktop (default) or"
		echo "                      headless mode."
		echo "EMULATOR_FRONT_CAM  - emulated|webcam0|webcam1|..."
		echo "                      instruct Android emulator to use either emulated image or host camera"
		echo "EMULATOR_BACK_CAM   - emulated|webcam0|webcam1|..."
		echo "                      instruct Android emulator to use either emulated image or host camera"
		echo "EMULATOR_PHONE      - 10-digit phone number; instruct Android emulator to use specified phone number"
    echo
    exit 254
fi

ANDROID_SDK_ROOT=~/.nexial/android/sdk
EMULATOR_PHONE_NUMBER=2136394251

EMU_OPTIONS=
if [[ -n ${EMULATOR_TIMEZONE}  ]] ; then EMU_OPTIONS="${EMU_OPTIONS} -timezone ${EMULATOR_TIMEZONE}" ; fi
if [[ -n ${EMULATOR_MEMORY}    ]] ; then EMU_OPTIONS="${EMU_OPTIONS} -memory ${EMULATOR_MEMORY}" ; fi
if [[ -n ${EMULATOR_HEADLESS}  ]] ; then EMU_OPTIONS="${EMU_OPTIONS} -no-window" ; fi
if [[ -n ${EMULATOR_FRONT_CAM} ]] ; then EMU_OPTIONS="${EMU_OPTIONS} -camera-front ${EMULATOR_FRONT_CAM}" ; fi
if [[ -n ${EMULATOR_BACK_CAM}  ]] ; then EMU_OPTIONS="${EMU_OPTIONS} -camera-back ${EMULATOR_BACK_CAM}" ; fi
if [[ -n ${EMULATOR_PHONE}     ]] ; then EMULATOR_PHONE_NUMBER=${EMULATOR_PHONE} ; fi
EMU_OPTIONS="$EMU_OPTIONS -phone-number ${EMULATOR_PHONE_NUMBER}"

cd ${ANDROID_SDK_ROOT}/emulator || exit
./emulator -avd $1 -ranchu -allow-host-audio ${EMU_OPTIONS}
