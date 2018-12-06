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
title "nexial project creator"
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
if [[ ! -s ${PROJECT_HOME}/.meta/project.id ]] ; then
    echo "» create project.id file under ${PROJECT_HOME}/.meta"
    echo $1 > ${PROJECT_HOME}/.meta/project.id
fi

SCRIPT_COUNT=`wc -l <(ls -1 ${PROJECT_HOME}/artifact/script/*.xlsx) | cut -d " " -f 8`
echo "script count is $SCRIPT_COUNT"
SKIP_DEF_SCRIPTS=true
if [[ "${SCRIPT_COUNT}" = "0" || "${SCRIPT_COUNT}" = "" ]] ; then
    echo "setting skip as false"
    SKIP_DEF_SCRIPTS=false
fi
if [[ ${SKIP_DEF_SCRIPTS} = true ]] ; then
    echo "» skip over the creation of default script/data files"
fi

if [[ $# -eq 1 ]] ; then
    if [[ ${SKIP_DEF_SCRIPTS} = false ]] ; then
        cp ${NEXIAL_HOME}/template/nexial-testplan.xlsx ${PROJECT_HOME}/artifact/plan/`basename $1`-plan.xlsx
        cp ${NEXIAL_HOME}/template/nexial-script.xlsx   ${PROJECT_HOME}/artifact/script/`basename $1`.xlsx
        cp ${NEXIAL_HOME}/template/nexial-data.xlsx     ${PROJECT_HOME}/artifact/data/`basename $1`.data.xlsx
    fi
else
    if [[ ${SKIP_DEF_SCRIPTS} = true ]] ; then
        # in dealing with existing project, we don't need to create default script/data files
        shift
    else
        cp ${NEXIAL_HOME}/template/nexial-testplan.xlsx ${PROJECT_HOME}/artifact/plan/`basename $1`-plan.xlsx
    fi

    while [[ "$1" != "" ]]; do
        script_name=`basename $1`
        echo "» create test script and data file for '${script_name}'"
        cp ${NEXIAL_HOME}/template/nexial-script.xlsx ${PROJECT_HOME}/artifact/script/${script_name}.xlsx
        cp ${NEXIAL_HOME}/template/nexial-data.xlsx   ${PROJECT_HOME}/artifact/data/${script_name}.data.xlsx
        shift
    done
fi

echo "» DONE - nexial automation project created as follows:"
echo

cd ${PROJECT_HOME}
chmod -fR 755 ${PROJECT_HOME}
pwd
ls -GRAF

echo
echo
