#!/bin/bash

# --------------------------------------------------------------------------------
# environment variable guide
# --------------------------------------------------------------------------------
# JAVA_HOME           - home directory of a valid JDK installation (1.6 or above)
# PROJECT_HOME        - home directory of your project.
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
if [ -d "${PROJECT_HOME}" ]; then
	PROJECT_CLASSPATH="${PROJECT_HOME}/classes:${PROJECT_HOME}/lib/*"
else
	PROJECT_CLASSPATH=
fi

# --------------------------------------------------------------------------------
# setting additional Java runtime options and classpath
# --------------------------------------------------------------------------------
# determine the browser type to use for this run - THIS PROPERTY OVERWRITES THE SAME SETTING IN EXCEL
# - valid types are: firefox, chrome, ie, safari
if [ "${BROWSER_TYPE}" != "" ]; then
	echo "setting BROWSER_TYPE as ${BROWSER_TYPE}"
	JAVA_OPT="${JAVA_OPT} -Dnexial.browser=${BROWSER_TYPE}"
fi

# webdriver settings
JAVA_OPT="${JAVA_OPT} -Dwebdriver.enable.native.events=true"
JAVA_OPT="${JAVA_OPT} -Dwebdriver.reap_profile=true"
JAVA_OPT="${JAVA_OPT} -Dwebdriver.accept.untrusted.certs=true"
JAVA_OPT="${JAVA_OPT} -Dwebdriver.assume.untrusted.issue=true"
JAVA_OPT="${JAVA_OPT} -Dwebdriver.winium.verbose=false"
JAVA_OPT="${JAVA_OPT} -Dwebdriver.winium.silent=true"
#JAVA_OPT="${JAVA_OPT} -Dwebdriver.winium.logpath=$TMPDIR\winium-service.log"

if [ "${CHROME_BIN}" = "" ]; then
	CHROME_BIN="${DEFAULT_CHROME_BIN}"
fi
echo "setting CHROME_BIN as ${CHROME_BIN}"

if [ "${FIREFOX_BIN}" = "" ]; then
	FIREFOX_BIN="${DEFAULT_FIREFOX_BIN}"
fi
echo "setting FIREFOX_BIN as ${FIREFOX_BIN}"

# interactive mode support
if [ "${NEXIAL_INTERACTIVE}" != "" ]; then
	JAVA_OPT="$JAVA_OPT -Dnexial.interactive=${NEXIAL_INTERACTIVE}"
fi

# --------------------------------------------------------------------------------
# run nexial now
# --------------------------------------------------------------------------------
#echo Runtime Option: ${JAVA_OPT} | sed 's/-D/~/g' | tr "~" "\n"
echo

set -x
${JAVA} \
	 -classpath "${PROJECT_CLASSPATH}:${NEXIAL_CLASSES}:${NEXIAL_LIB}/nexial*.jar:${NEXIAL_LIB}/*" \
	 ${JAVA_OPT} \
	 -Dwebdriver.chrome.bin="${CHROME_BIN}" \
	 -Dwebdriver.firefox.bin="${FIREFOX_BIN}" \
  org.nexial.core.Nexial $*
set +x
exit $?
