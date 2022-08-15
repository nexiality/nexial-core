#!/bin/bash

function reportBadInputAndExit() {
    echo
    echo "ERROR: Required input not found."
    echo "USAGE: $0 [project name] [optional: testcase id, testcase id, ...]"
    echo
    echo " [project_name]        is required. Note that this can be just the name of the"
    echo "                       project - which will automatically be created under ${PROJECT_BASE} -"
    echo "                       or, a PREEXISTING, fully qualified path."
    echo " [script_name]         is the script to create. Nexial will copy the script"
    echo "                       template to the specified project based on this name."
    echo "                       A corresponding data file will also be created."
    echo "                       Note: this is optional."
    echo " macro:[macro_name]    is the macro to create. Nexial will copy the macro"
    echo "                       template to the specified project based on this name."
    echo "                       Note: this is optional."
    echo ""
    echo " It is possible to specify multiple scripts and macros to create. For example,"
    echo ""
    echo "	${sh_name} my_project Script1 macro:CommonLib Script2 macro:Navigations ..."
    echo ""
    echo ""
    exit -1
}

NEXIAL_HOME=$(cd `dirname $0`/..; pwd -P)
. ${NEXIAL_HOME}/bin/.commons.sh
title "nexial project artifact creator"
checkJava
resolveEnv

# set up
sh_name=$0
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
	if [[ "${TMP_PROJECT_HOME}" == *"${HOME}/"* ]] ; then
		mkdir -p "${TMP_PROJECT_HOME}"
		PROJECT_HOME="$TMP_PROJECT_HOME"
	else
		PROJECT_HOME=${PROJECT_BASE}/$1
	fi
fi

PROJECT_NAME="`basename "${PROJECT_HOME}"`"
echo "  PROJECT_HOME:   ${PROJECT_HOME}"

# create project.id file to uniquely identify a "project" across enterprise (i.e. same SCM)
PROJECT_ID="${PROJECT_HOME}/.meta/project.id"
if [[ -f "${PROJECT_ID}" ]] ; then
    PROJECT_NAME=$(cat "${PROJECT_ID}" | tr -dc '[[:print:]]')
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

## create empty project.properties file if it does not exist
if [[ -e "${PROJECT_HOME}/artifact/project.properties" ]]; then
    echo "» SKIPPED creating project.properties; already exist"
else
    echo "» creating project.properties"
    touch "${PROJECT_HOME}/artifact/project.properties"
fi

## create default plan file, if none exists
if [[ `find ${PROJECT_HOME}/artifact/plan -name "*.xlsx" | wc -l | sed 's/ //g'` -gt 0 ]] ; then
    echo "» SKIPPED creating default plan file; already exist"
else
    echo "» creating default plan file"
    cp -n ${NEXIAL_HOME}/template/nexial-testplan.xlsx "${PROJECT_HOME}/artifact/plan/${PROJECT_NAME}-plan.xlsx"
fi

## create default script file, if none exists
if [[ `find ${PROJECT_HOME}/artifact/script -name "*.xlsx" | wc -l | sed 's/ //g'` -gt 0 ]] ; then
    echo "» SKIPPED creating default script file; already exist"
else
    echo "» creating default script file"
    cp -n ${NEXIAL_HOME}/template/nexial-script.xlsx "${PROJECT_HOME}/artifact/script/${PROJECT_NAME}.xlsx"
fi

## create default data file, if none exists
if [[ `find ${PROJECT_HOME}/artifact/data -name "*.xlsx" | wc -l | sed 's/ //g'` -gt 0 ]] ; then
    echo "» SKIPPED creating default data file; already exist"
else
    echo "» creating default data file"
    cp -n ${NEXIAL_HOME}/template/nexial-data.xlsx "${PROJECT_HOME}/artifact/data/${PROJECT_NAME}.data.xlsx"
fi

## create default macro file, if none exists
if [[ `find ${PROJECT_HOME}/artifact/script -name "${PROJECT_NAME}.macro.xlsx" | wc -l | sed 's/ //g'` -gt 0 ]] ; then
    echo "» SKIPPED creating default macro file; already exist"
else
    echo "» creating default macro file"
    cp -n ${NEXIAL_HOME}/template/nexial-macro.xlsx "${PROJECT_HOME}/artifact/script/${PROJECT_NAME}.macro.xlsx"
fi

## create default run-script.sh, if none exists
if [[ `find ${PROJECT_HOME}/artifact/bin -name "run-script.sh" | wc -l | sed 's/ //g'` -gt 0 ]] ; then
    echo "» SKIPPED creating run-script.sh; already exist"
else
    echo "» creating run-script.sh"
    sed -e "s/%PROJECT_NAME%/${PROJECT_NAME}/g" ${NEXIAL_HOME}/template/run-script.sh.txt > ${PROJECT_HOME}/artifact/bin/run-script.sh
fi

## create default run-plan.sh, if none exists
if [[ `find ${PROJECT_HOME}/artifact/bin -name "run-plan.sh" | wc -l | sed 's/ //g'` -gt 0 ]] ; then
    echo "» SKIPPED creating run-plan.sh; already exist"
else
    echo "» creating run-plan.sh"
    sed -e "s/%PROJECT_NAME%/${PROJECT_NAME}/g" ${NEXIAL_HOME}/template/run-plan.sh.txt > ${PROJECT_HOME}/artifact/bin/run-plan.sh
fi

## create script/data/macro based on input
shift
while [[ "$1" != "" ]]; do
    script_name=$1
    if [[ ${script_name} = macro:* ]] ; then
        macro_name=`echo ${script_name} | sed -e 's/^macro://g'`
        if [[ `find ${PROJECT_HOME}/artifact/script -name "${macro_name}.xlsx" | wc -l | sed 's/ //g'` -gt 0 ]] ; then
            echo "» SKIPPED creating macro ${macro_name}.xlsx; already exist"
        else
            echo "» creating macro ${macro_name}.xlsx"
            cp -n ${NEXIAL_HOME}/template/nexial-macro.xlsx "${PROJECT_HOME}/artifact/script/${macro_name}.xlsx"
        fi
    else
        echo `find ${PROJECT_HOME}/artifact/script -name "${script_name}.xlsx" | wc -l | sed 's/ //g'`
        if [[ `find ${PROJECT_HOME}/artifact/script -name "${script_name}.xlsx" | wc -l | sed 's/ //g'` -gt 0 ]] ; then
            echo "» SKIPPED creating script ${script_name}.xlsx; already exist"
        else
            echo "» creating script ${script_name}.xlsx"
            cp -n ${NEXIAL_HOME}/template/nexial-script.xlsx "${PROJECT_HOME}/artifact/script/${script_name}.xlsx"
        fi

        if [[ `find ${PROJECT_HOME}/artifact/data -name "${script_name}.data.xlsx" | wc -l | sed 's/ //g'` -gt 0 ]] ; then
            echo "» SKIPPED creating data file ${script_name}.data.xlsx; already exist"
        else
            echo "» creating data file ${script_name}.data.xlsx"
            cp -n ${NEXIAL_HOME}/template/nexial-data.xlsx "${PROJECT_HOME}/artifact/data/${script_name}.data.xlsx"
        fi
    fi
    shift
done

echo "» DONE - nexial automation project created as follows:"
echo

cd "${PROJECT_HOME}"
chmod -fR 755 "${PROJECT_HOME}"
find "${PROJECT_HOME}" -name "*.xlsx" -o -name "*.properties" -o -name "*.sh" | sort -n

echo
echo
