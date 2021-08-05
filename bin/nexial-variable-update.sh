#!/usr/bin/env bash

NEXIAL_HOME=$(cd `dirname $0`/..; pwd -P)
. ${NEXIAL_HOME}/bin/.commons.sh
title "nexial data variable updater"
checkJava
resolveEnv

args=""
for var in "$@"; do args="$args \"$var\""; done

# download nexial-lib-x.x.zip to userhome/.nexial/lib
echo
eval "$NEXIAL_HOME/bin/nexial-lib-downloader.sh"
ret=$?
if [ $ret -ne 0 ]; then
    exit $ret
fi

# run now
echo
echo

eval ${JAVA} -classpath "${NEXIAL_CLASSES}:${NEXIAL_LIB}/nexial*.jar:${NEXIAL_LIB}/*:${USER_HOME_NEXIAL_LIB}/*" ${JAVA_OPT} org.nexial.core.tools.DataVariableUpdater $args
ret=$?

echo
echo

exit ${ret}
