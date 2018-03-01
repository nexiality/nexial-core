#!/bin/bash

function reportBadInputAndExit() {
	echo
    echo ERROR: Required input not found.
    echo USAGE: $0 [project name] [optional: testcase id, testcase id, ...]
    echo
    echo
    exit -1
}

NEXIAL_HOME=$(cd `dirname $0`/..; pwd -P)
. ${NEXIAL_HOME}/bin/.commons.sh
title "nexial runner"
title "nexial project creator"
checkJava
resolveEnv

if [[ "$1" = "" ]] ; then
	reportBadInputAndExit
fi

PROJECT_HOME=${PROJECT_BASE}/$1
echo "  PROJECT_HOME: ${PROJECT_HOME}"

echo
echo "» creating project home at ${PROJECT_HOME}"
mkdir -p ${PROJECT_HOME}/artifact/script > /dev/null 2>&1
mkdir -p ${PROJECT_HOME}/artifact/data > /dev/null 2>&1
mkdir -p ${PROJECT_HOME}/artifact/plan > /dev/null 2>&1
mkdir -p ${PROJECT_HOME}/output > /dev/null 2>&1

cp ${NEXIAL_HOME}/template/nexial-testplan.xlsx ${PROJECT_HOME}/artifact/plan/$1.xlsx

while [ "$1" != "" ] ; do
	echo "» create test script for $1"
	cp ${NEXIAL_HOME}/template/nexial-script.xlsx ${PROJECT_HOME}/artifact/script/$1.xlsx
	cp ${NEXIAL_HOME}/template/nexial-data.xlsx ${PROJECT_HOME}/artifact/data/$1.data.xlsx
	shift
done

chmod -fR 755 ${PROJECT_HOME}

${NEXIAL_HOME}/bin/nexial-script-update.sh -v -t ${PROJECT_HOME}

cd ${PROJECT_HOME}
echo
echo "» DONE"
