#!/usr/bin/env bash

NEXIAL_HOME=$(cd `dirname $0`/..; pwd -P)
. ${NEXIAL_HOME}/bin/.commons.sh
title "nexial setup"
checkJava
resolveEnv

# run now
echo
echo

${JAVA} -classpath "${NEXIAL_CLASSES}:${NEXIAL_LIB}/nexial*.jar:${NEXIAL_LIB}/*" ${JAVA_OPT} org.nexial.core.tools.NexialSetup $*
ret=$?

echo
echo

exit ${ret}
