#!/bin/bash

# environment variable guide
# JAVA_HOME           - home directory of JDK (1.8.0_152 or above)
# PROJECT_HOME        - home directory of your project.
# NEXIAL_OUTPUT       - the output directory (optional)
# --------------------------------------------------------------------------------

NEXIAL_HOME=$(cd `dirname $0`/..; pwd -P)

if [ "$1" == "-version" ] && [ "$2" == "" ]
then
 cat ${NEXIAL_HOME}/version.txt
 echo ""
 exit 0
else
 for ((i=1; i<=$#; i++))
 do
   if [ "${!i}" == "-version" ]
   then
     echo 'ERROR: Argument "-version" cannot be used with any other argument.'
     exit 1
   fi;
 done
fi;

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

if [[ "${CHROME_BIN}" == "" ]]; then CHROME_BIN="${DEFAULT_CHROME_BIN}"; fi
if [[ "${CHROME_BIN}" != "" ]]; then echo "setting CHROME_BIN  as ${CHROME_BIN}"; fi

if [[ "${FIREFOX_BIN}" == "" ]]; then FIREFOX_BIN="${DEFAULT_FIREFOX_BIN}"; fi
if [[ "${FIREFOX_BIN}" != "" ]]; then echo "setting FIREFOX_BIN as ${FIREFOX_BIN}"; fi


# support JVM max mem config
if [[ -n "${NEXIAL_MAX_MEM}" ]]; then export MAX_MEM=-Xmx${NEXIAL_MAX_MEM}; fi


# support environment default for output base directory
if [[ -n "${NEXIAL_OUTPUT}" ]]; then
  mkdir -p "${NEXIAL_OUTPUT}"
  export JAVA_OPT="${JAVA_OPT} -Dnexial.defaultOutBase=${NEXIAL_OUTPUT}"
fi


# sync nexial execution stats to environment variables
if [[ "${NEXIAL_POST_EXEC_SHELL}" != "" ]]; then
  echo "setting post exec shell script to ${NEXIAL_POST_EXEC_SHELL}"
  export JAVA_OPT="${JAVA_OPT} -Dnexial.postExecEnv=${NEXIAL_POST_EXEC_SHELL}"
fi

# download nexial-lib-x.x.zip to userhome/.nexial/lib
${NEXIAL_HOME}/bin/nexial-lib-downloader.sh
rc=$?
if [ $rc -ne 0 ]; then
    exit $rc
fi

rm -f ${USER_NEXIAL_KEYSTORE} /dev/null 2>&1

# run nexial now
echo

runNexial='${JAVA} ${MAX_MEM} \
    -classpath "${PROJECT_CLASSPATH}:${NEXIAL_CLASSES}:${NEXIAL_LIB}/nexial*.jar:${USER_NEXIAL_JAR}/*:${NEXIAL_LIB}/*:${USER_NEXIAL_LIB}/*" \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+ExplicitGCInvokesConcurrent \
    -Dwebdriver.chrome.bin="`echo ${CHROME_BIN} | sed '"'s/\ /\\\ /g'"'`" \
    -Dwebdriver.firefox.bin="`echo ${FIREFOX_BIN} | sed '"'s/\ /\\\ /g'"'`" \
    -Djava.library.path="${USER_NEXIAL_DLL}" \
    ${JAVA_OPT} \
    org.nexial.core.Nexial $*'
eval "$runNexial"
rc=$?

NEXIAL_INSTALLER_HOME="$HOME/projects/nexial-installer"

# run nexial update now
echo

UPDATE_AGREED=${USER_NEXIAL_INSTALL}/update-now
if test -f "$UPDATE_AGREED"; then
  presentDir=eval "pwd"
  echo "Updating nexial-core..."
  rm -rf "$UPDATE_AGREED"
  eval "${JAVA} -jar $NEXIAL_INSTALLER_HOME/lib/nexial-installer.jar upgradeNexial"
  cd "$presentDir"
fi

RESUME_FILE=${USER_NEXIAL_INSTALL}/resume-after-update
if test -f "$RESUME_FILE"; then
  echo "Resuming nexial-core execution."
  rm -rf "$RESUME_FILE"
  eval "$NEXIAL_HOME/bin/nexial.sh $*"
  rc=$?
fi

echo
echo

exit ${rc}
