#!/usr/bin/env bash

# -----------------------------------------------------------------------------------------------
# Show appId, appPackage and appActivity for the specified .apk file
# -----------------------------------------------------------------------------------------------

NEXIAL_HOME=$(cd `dirname $0`/../..; pwd -P)
. ${NEXIAL_HOME}/bin/.commons.sh
title "nexial APK manifest"
checkJava
resolveEnv

# these env. are changed on .commons.sh; resetting them back
export NEXIAL_HOME=$(cd `dirname $0`/../..; pwd -P)
export NEXIAL_LIB=${NEXIAL_HOME}/lib
export NEXIAL_CLASSES=${NEXIAL_HOME}/classes
export USER_HOME_NEXIAL_LIB=~/.nexial/lib

# download nexial-lib-x.x.zip to userhome/.nexial/lib
$NEXIAL_HOME/bin/nexial-lib-downloader.sh
rc=$?
if [ $rc -ne 0 ]; then
    exit $rc
fi

# run now
${JAVA} -classpath "${NEXIAL_CLASSES}:${NEXIAL_LIB}/nexial*.jar:${NEXIAL_LIB}/*:${USER_HOME_NEXIAL_LIB}/*" ${JAVA_OPT} \
	org.nexial.core.tools.appium.ApkAnalyzer $*
exit $?
