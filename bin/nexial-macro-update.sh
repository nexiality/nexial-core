#!/usr/bin/env bash

NEXIAL_HOME=$(cd `dirname $0`/..; pwd -P)
. ${NEXIAL_HOME}/bin/.commons.sh
title "nexial macro updater"
checkJava
resolveEnv

args=""
for var in "$@"; do args="$args \"$var\""; done

# run now
echo
echo

eval ${JAVA} -classpath "${NEXIAL_CLASSES}:${NEXIAL_LIB}/nexial*.jar:${NEXIAL_LIB}/*" ${JAVA_OPT} org.nexial.core.tools.MacroUpdater ${args}
ret=$?

echo
echo

exit ${ret}
