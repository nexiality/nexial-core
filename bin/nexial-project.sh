#!/bin/bash

function reportBadInputAndExit() {
    echo
    echo ERROR: Required input not found.
    echo USAGE: $0 [project name] [optional: testcase id, testcase id, ...]
    echo
    exit -1
}

NEXIAL_HOME=$(cd `dirname $0`/..; pwd -P)
. ${NEXIAL_HOME}/bin/.commons.sh
title "nexial project artifact creator"
checkJava
resolveEnv

# set up
if [[ "$1" = "" ]]; then
    reportBadInputAndExit
fi

# allow user to specify project home - if it exists
PROJECT_HOME=
if [[ -d "$1" ]]; then
    PROJECT_HOME="$1"
else
    PROJECT_HOME=${PROJECT_BASE}/$1
fi
echo "  PROJECT_HOME:   ${PROJECT_HOME}"

echo
echo "» (re)creating project home at ${PROJECT_HOME}"
mkdir -p ${PROJECT_HOME}/.meta > /dev/null 2>&1
mkdir -p ${PROJECT_HOME}/artifact/script > /dev/null 2>&1
mkdir -p ${PROJECT_HOME}/artifact/data > /dev/null 2>&1
mkdir -p ${PROJECT_HOME}/artifact/plan > /dev/null 2>&1
mkdir -p ${PROJECT_HOME}/output > /dev/null 2>&1

# create project.id file to uniquely identify a "project" across enterprise (i.e. same SCM)
PROJECT_ID=${PROJECT_HOME}/.meta/project.id
if [[ ! -s ${PROJECT_ID} ]] ; then
    echo "» create ${PROJECT_ID}"
    echo `basename $1` > ${PROJECT_ID}
fi

SKIP_DEF_SCRIPTS=true
for f in ${PROJECT_HOME}/artifact/script/*.xlsx; do
    ## Check if the glob gets expanded to existing files.
    ## If not, f here will be exactly the pattern above
    ## and the exists test will evaluate to false.
    [[ -e "$f" ]] && SKIP_DEF_SCRIPTS=true || SKIP_DEF_SCRIPTS=false

    ## This is all we needed to know, so we can break after the first iteration
    break
done

if [[ ${SKIP_DEF_SCRIPTS} = true ]] ; then
    echo "» skip over the creation of default script/data files"

    # in dealing with existing project, we don't need to create default script/data files
    shift
else
    cp -n ${NEXIAL_HOME}/template/nexial-testplan.xlsx "${PROJECT_HOME}/artifact/plan/`basename $1`-plan.xlsx"
fi

while [[ "$1" != "" ]]; do
    script_name="$1"
    echo "» create test script and data file for '${script_name}'"
    cp -n ${NEXIAL_HOME}/template/nexial-script.xlsx "${PROJECT_HOME}/artifact/script/${script_name}.xlsx"
    cp -n ${NEXIAL_HOME}/template/nexial-data.xlsx   "${PROJECT_HOME}/artifact/data/${script_name}.data.xlsx"
    shift
done

echo "» DONE - nexial automation project created as follows:"
echo

cd ${PROJECT_HOME}
chmod -fR 755 ${PROJECT_HOME}
find ${PROJECT_HOME} | sort -n

echo
echo
