#!/bin/bash

# environment variable guide
# JAVA_HOME           - home directory of JDK (1.8.0_152 or above)
# PROJECT_HOME        - home directory of your project.
# NEXIAL_OUTPUT       - the output directory (optional)
# --------------------------------------------------------------------------------

NEXIAL_HOME=$(cd `dirname $0`/..; pwd -P)
. ${NEXIAL_HOME}/bin/.commons.sh
title "nexial runner"
checkJava
resolveEnv


# setting project classpath (classes & lib)
# giving priority to project-specific classpaths
PROJECT_CLASSPATH=
if [[ -d "${PROJECT_HOME}" ]]; then
	PROJECT_CLASSPATH="${PROJECT_HOME}/classes:${PROJECT_HOME}/lib/*"
else
	PROJECT_CLASSPATH=
fi

if [[ "${CHROME_BIN}" = "" ]]; then CHROME_BIN="${DEFAULT_CHROME_BIN}"; fi
echo "setting CHROME_BIN  as ${CHROME_BIN}"

if [[ "${FIREFOX_BIN}" = "" ]]; then FIREFOX_BIN="${DEFAULT_FIREFOX_BIN}"; fi
echo "setting FIREFOX_BIN as ${FIREFOX_BIN}"


# support JVM max mem config
if [[ -n "${NEXIAL_MAX_MEM}" ]]; then export MAX_MEM=-Xmx${NEXIAL_MAX_MEM}; fi


# support environment default for output base directory
if [[ ! -z "${NEXIAL_OUTPUT}" ]] ; then
    mkdir -p "${NEXIAL_OUTPUT}"
    export JAVA_OPT="${JAVA_OPT} -Dnexial.defaultOutBase=${NEXIAL_OUTPUT}"
fi


# sync nexial execution stats to environment variables
if [[ "${NEXIAL_POST_EXEC_SHELL}" != "" ]] ; then
    echo "setting post exec shell script to ${NEXIAL_POST_EXEC_SHELL}"
    export JAVA_OPT="${JAVA_OPT} -Dnexial.postExecEnv=${NEXIAL_POST_EXEC_SHELL}"
fi


# run nexial now
echo

eval ${JAVA} ${MAX_MEM} \
    -classpath "${PROJECT_CLASSPATH}:${NEXIAL_CLASSES}:${NEXIAL_LIB}/nexial*.jar:${NEXIAL_LIB}/*" \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+ExplicitGCInvokesConcurrent \
    -Dwebdriver.chrome.bin="`echo ${CHROME_BIN} | sed 's/\ /\\\ /g'`" \
    -Dwebdriver.firefox.bin="`echo ${FIREFOX_BIN} | sed 's/\ /\\\ /g'`" \
    ${JAVA_OPT} \
    org.nexial.core.Nexial $*
rc=$?

echo
echo

exit ${rc}
