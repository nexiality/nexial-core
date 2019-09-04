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
PROJECT_NAME=
PROJECT_HOME=
TMP_PROJECT_HOME="$1"
TMP_PROJECT_HOME="`echo ${TMP_PROJECT_HOME/\~/$HOME}`"
if [[ -d "$TMP_PROJECT_HOME" ]]; then
    PROJECT_HOME="$TMP_PROJECT_HOME"
else
    PROJECT_HOME=${PROJECT_BASE}/$1
fi
PROJECT_NAME="`basename "${PROJECT_HOME}"`"
echo "  PROJECT_HOME:   ${PROJECT_HOME}"
#echo "  PROJECT_NAME:   ${PROJECT_NAME}"

# create project.id file to uniquely identify a "project" across enterprise (i.e. same SCM)
PROJECT_ID="${PROJECT_HOME}/.meta/project.id"
if [[ -f "${PROJECT_ID}" ]] ; then
  PROJECT_NAME=$(cat "${PROJECT_ID}")
fi

echo
echo "» currently project id is set to '${PROJECT_NAME}'."
echo "» to change it, enter new project id below or press ENTER to keep it as is"
if [[ "${NEXIAL_OS}" = "Mac" ]]; then
    echo -n "  Enter project id [${PROJECT_NAME}]: "
    read user_project_name
else
    read -p "  Enter project id [${PROJECT_NAME}]: " user_project_name
fi

if [[ "${user_project_name}" != "" ]]; then
    PROJECT_NAME="${user_project_name}"
fi

echo
echo "» (re)creating project home at ${PROJECT_HOME}"
mkdir -p "${PROJECT_HOME}/.meta" > /dev/null 2>&1
mkdir -p "${PROJECT_HOME}/artifact/bin" > /dev/null 2>&1
mkdir -p "${PROJECT_HOME}/artifact/script" > /dev/null 2>&1
mkdir -p "${PROJECT_HOME}/artifact/data" > /dev/null 2>&1
mkdir -p "${PROJECT_HOME}/artifact/plan" > /dev/null 2>&1
mkdir -p "${PROJECT_HOME}/output" > /dev/null 2>&1

echo "» (re)creating ${PROJECT_ID} with ${PROJECT_NAME}"
echo "${PROJECT_NAME}" > "${PROJECT_ID}"

SKIP_DEF_SCRIPTS=true
for f in "${PROJECT_HOME}/artifact/script/*.xlsx"; do
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
    cp -n ${NEXIAL_HOME}/template/nexial-testplan.xlsx "${PROJECT_HOME}/artifact/plan/${PROJECT_NAME}-plan.xlsx"
fi

# first, create script/data files based on project name (if needed)
if $(find "${PROJECT_HOME}/artifact/script" -mindepth 1 2>/dev/null | read); then
    echo "» skip over the creation of default script/data files on ${PROJECT_HOME}"
else
    echo "» create test script and data file for '${PROJECT_NAME}' on ${PROJECT_HOME}"
    cp -n ${NEXIAL_HOME}/template/nexial-script.xlsx "${PROJECT_HOME}/artifact/script/${PROJECT_NAME}.xlsx"
    cp -n ${NEXIAL_HOME}/template/nexial-data.xlsx   "${PROJECT_HOME}/artifact/data/${PROJECT_NAME}.data.xlsx"
fi
shift

while [[ "$1" != "" ]]; do
    script_name="$1"
    echo "» create test script and data file for '${script_name}'"
    cp -n ${NEXIAL_HOME}/template/nexial-script.xlsx "${PROJECT_HOME}/artifact/script/${script_name}.xlsx"
    cp -n ${NEXIAL_HOME}/template/nexial-data.xlsx   "${PROJECT_HOME}/artifact/data/${script_name}.data.xlsx"
    shift
done

echo "» DONE - nexial automation project created as follows:"
echo

cd "${PROJECT_HOME}"
chmod -fR 755 "${PROJECT_HOME}"
find "${PROJECT_HOME}" -name "*.xlsx" | sort -n

echo
echo
