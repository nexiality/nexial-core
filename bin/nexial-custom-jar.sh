#!/bin/bash

function reportBadInputAndExit() {
    echo
    echo "ERROR: Required input not found."
    echo "USAGE: $0 [directory|file]"
    echo "       $0 [directory|file] [directory|file] [...]"
    echo
    echo "Files copied from the specified location to ${NEXIAL_JAR}"
    echo "will be used as additional libraries (jars) in your Nexial execution."
    echo
    exit -1
}

function checkPrompt() {
    response=
    if [[ "${NEXIAL_OS}" = "Mac" ]]; then
        echo -n "» proceed with the copying (existing files will overwritten)? (Y/N) "
        read response
    else
        read -p "» proceed with the copying (existing files will overwritten)? (Y/N) " response
    fi
    if [[ "$response" = "N" || "$response" = "n" ]]; then
        echo "» copy cancelled..."
        echo
    elif [[ "$response" = "Y" || "$response" = "y" ]]; then
        doCopy
    else
        checkPrompt
    fi
}

function doCopy() {
    echo "» copying from ${source}"
    echo "--------------------------------------------------------------------------------"
    cp -pPRv ${source_jar} ${NEXIAL_JAR}
    echo "--------------------------------------------------------------------------------"
    echo
}

NEXIAL_HOME=$(cd `dirname $0`/..; pwd -P)
. ${NEXIAL_HOME}/bin/.commons.sh
title "nexial custom library setup"
checkJava
resolveEnv
NEXIAL_JAR=~/.nexial/jar

# set up
if [[ "$1" = "" ]]; then
    reportBadInputAndExit
fi

# first, create script/data files based on project name (if needed)
if [[ -d "${NEXIAL_JAR}" ]] ; then
    echo "» found ${NEXIAL_JAR}"
    echo
else
    echo "» creating missing directory - ${NEXIAL_JAR}"
    echo
    mkdir -p ${NEXIAL_JAR} > /dev/null 2>&1
fi

while [[ "$1" != "" ]]; do
    source="$1"
    if [[ ! -e "$1" ]]; then
        echo "» $1 not found."
        reportBadInputAndExit
    elif [[ -d "$1" ]]; then
        source_jar="$1/*"
    else
        source_jar="$1"
    fi

    echo "» following files/directories are being copied to ${NEXIAL_JAR}"
    ls -FA1 $1
    echo
    checkPrompt
    shift
done
