#!/usr/bin/env bash

NEXIAL_HOME=$(cd `dirname $0`/..; pwd -P)
. ${NEXIAL_HOME}/bin/.commons.sh
title "nexial java-ui mapping launcher"
checkJava
resolveEnv

# download nexial-lib-x.x.zip to userhome/.nexial/lib
$NEXIAL_HOME/bin/nexial-lib-downloader.sh
ret=$?
if [ $ret -ne 0 ]; then
    exit $ret
fi

# run now
echo
echo
${JAVA} -classpath "${NEXIAL_CLASSES}:${NEXIAL_LIB}/nexial*.jar:${NEXIAL_LIB}/*:${USER_NEXIAL_LIB}/*" \
	${JAVA_OPT} \
	org.nexial.core.tools.JavaUIMapping $*
ret=$?
echo
echo

exit ${ret}
