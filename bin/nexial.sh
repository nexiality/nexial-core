#!/bin/bash

# --------------------------------------------------------------------------------
# environment variable guide
# --------------------------------------------------------------------------------
# JAVA_HOME           - home directory of JDK (1.8.0_152 or above)
# PROJECT_HOME        - home directory of your project.
# NEXIAL_OUTPUT       - the output directory (optional)
# --------------------------------------------------------------------------------

NEXIAL_HOME=$(cd `dirname $0`/..; pwd -P)
. ${NEXIAL_HOME}/bin/.commons.sh
title "nexial runner"
checkJava
resolveEnv


# --------------------------------------------------------------------------------
# setting project classpath (classes & lib)
# --------------------------------------------------------------------------------
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


# --------------------------------------------------------------------------------
# change JVM memory
# --------------------------------------------------------------------------------
if [[ -n "${NEXIAL_MAX_MEM}" ]]; then export MAX_MEM=-Xmx${NEXIAL_MAX_MEM}; fi


# --------------------------------------------------------------------------------
# run nexial now
# --------------------------------------------------------------------------------
echo

if [[ ! -z "${NEXIAL_OUTPUT}" ]] ; then
    mkdir -p "${NEXIAL_OUTPUT}"
    export JAVA_OPT="${JAVA_OPT} -Dnexial.defaultOutBase=${NEXIAL_OUTPUT}"
fi

eval ${JAVA} ${MAX_MEM} \
    -classpath "${PROJECT_CLASSPATH}:${NEXIAL_CLASSES}:${NEXIAL_LIB}/nexial*.jar:${NEXIAL_LIB}/*" \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+ExplicitGCInvokesConcurrent \
    -Dwebdriver.chrome.bin="`echo ${CHROME_BIN} | sed 's/\ /\\\ /g'`" \
    -Dwebdriver.firefox.bin="`echo ${FIREFOX_BIN} | sed 's/\ /\\\ /g'`" \
    ${JAVA_OPT} \
    org.nexial.core.Nexial $*


# create just-in-time batch file to execute nexial scripts
# if [[ -z ${NEXIAL_OUTPUT} ]] ; then
#     EXEC_SH="${TMPDIR}/${RANDOM}-nexial.sh"
#     export JAVA_OPT="${JAVA_OPT} -Dnexial.script=${EXEC_SH}"
# else
#     mkdir -p "${NEXIAL_OUTPUT}"
#     EXEC_SH="${NEXIAL_OUTPUT}/nexial.sh"
#     export JAVA_OPT="${JAVA_OPT} -Dnexial.script=${EXEC_SH}"
#     export JAVA_OPT="${JAVA_OPT} -Dnexial.defaultOutBase=${NEXIAL_OUTPUT}"
# fi

# echo "#!/bin/sh" > ${EXEC_SH}
# echo -n ${JAVA} \
#     ${MAX_MEM} -classpath "${PROJECT_CLASSPATH}:${NEXIAL_CLASSES}:${NEXIAL_LIB}/nexial*.jar:${NEXIAL_LIB}/*" \
#     -XX:+UnlockExperimentalVMOptions -XX:+ExplicitGCInvokesConcurrent \
#     -Dwebdriver.chrome.bin="`echo ${CHROME_BIN} | sed 's/\ /\\\ /g'`" \
#     -Dwebdriver.firefox.bin="`echo ${FIREFOX_BIN} | sed 's/\ /\\\ /g'`" >> ${EXEC_SH}
# echo -n " ${JAVA_OPT}" >> ${EXEC_SH}
# echo -n " org.nexial.core.Nexial $*" >> ${EXEC_SH}

# chmod -f 755 ${EXEC_SH}

# ${EXEC_SH}
rc=$?

# rm -f ${EXEC_SH}

echo
echo
echo

exit ${rc}
