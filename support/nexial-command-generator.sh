#!/bin/bash

NEXIAL_HOME=$(cd `dirname $0`/..; pwd -P)
. ${NEXIAL_HOME}/bin/.commons.sh
title "nexial command generator"
checkJava
resolveEnv

${JAVA} -classpath "${NEXIAL_CLASSES}:${NEXIAL_LIB}/*" ${JAVA_OPT} \
	org.nexial.core.tools.CommandMetaGenerator -v
