#!/bin/bash

NEXIAL_HOME=$(cd `dirname $0`/..; pwd -P)
. ${NEXIAL_HOME}/bin/.commons.sh
title "nexial artifact repair"
checkJava
resolveEnv

# download nexial-lib-x.x.zip to userhome/.nexial/lib
$NEXIAL_HOME/bin/nexial-lib-downloader.sh
rc=$?
if [ $rc -ne 0 ]; then
    exit $rc
fi

# run now
echo
${JAVA} -classpath "${NEXIAL_CLASSES}:${NEXIAL_LIB}/nexial*.jar:${NEXIAL_LIB}/*:${USER_HOME_NEXIAL_LIB}/*" ${JAVA_OPT} \
	org.nexial.core.tools.repair.RepairArtifact $*
rc=$?

exit ${rc}
