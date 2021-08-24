#!/usr/bin/env bash

#
# Generated by ${generator.signature} on ${generated.timestamp}
#
# For more details and options on Android emulator, check out
# 	https://developer.android.com/studio/run/emulator-commandline
#

# For more details on these environment variables, check out
#     https://nexiality.github.io/documentation/commands/mobile/batch-files/run-android-emulator
# EMULATOR_TIMEZONE=
# EMULATOR_MEMORY=
# EMULATOR_HEADLESS=
# EMULATOR_FRONT_CAM=
# EMULATOR_BACK_CAM=
# EMULATOR_PHONE=

if [[ -z ${NEXIAL_HOME} ]] ; then NEXIAL_HOME=~/projects/nexial-core ; fi
${NEXIAL_HOME}/bin/mobile/run-android-emulator.sh ${avd.id}
