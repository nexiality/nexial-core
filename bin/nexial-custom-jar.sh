#!/bin/bash

function reportBadInputAndExit() {
    echo
    echo ERROR: Required input not found.
    echo USAGE: $0 [source directory/file]
    echo
    echo This command will copy files/directories from source location to ${NEXIAL_JAR}.
    echo You can also provide one or more source locations as like $0 [source directory/file] [source directory/file]
    exit -1
}

function checkPrompt() {
    echo /--------------------------------------------------------------------------------\\
    if [[ "${NEXIAL_OS}" = "Mac" ]]; then
      echo -n "  Do you still want to continue copy '(Y/N)'? : "
      read response
    else
      read -p "  Do you still want to continue copy '(Y/N)'? : " response
    fi
    echo \\--------------------------------------------------------------------------------/

    if [ "$response" = "N" ] || [ "$response" = "n" ]
    then
      echo "» COPY CANCELLED..."
      echo

    elif [ "$response" = "Y" ] || [ "$response" = "y" ];
    then
        doCopy
    else
        checkPrompt
    fi
}

function doCopy() {
    echo "» copy jar/s from directory or file '${source}'"
    cp -r ${source_jar} ${NEXIAL_JAR}
    echo "» COPY DONE..."
    echo
}

NEXIAL_HOME=$(cd `dirname $0`/..; pwd -P)
. ${NEXIAL_HOME}/bin/.commons.sh
title "copy nexial custom jars"
checkJava
resolveEnv

NEXIAL_JAR=~/.nexial/jar
echo ${NEXIAL_JAR}

# set up
if [[ "$1" = "" ]]; then
    reportBadInputAndExit
fi

# first, create script/data files based on project name (if needed)
if $(find "${NEXIAL_JAR}" -mindepth 1 2>/dev/null | read); then
    echo "» skip over the creation of directory '${NEXIAL_JAR}'"
else
	echo "» Creating directory '${NEXIAL_JAR}'"
  mkdir -p ${NEXIAL_JAR} > /dev/null 2>&1
fi

while [[ "$1" != "" ]]; do
    source="$1"
    if [ ! -e "$1" ]
    then
      echo "» File/directory '$1' not found."
      reportBadInputAndExit
    elif [ -d "$1" ]
    then
        source_jar="$1/*"
    else
       source_jar="$1"
    fi

    echo /--------------------------------------------------------------------------------\\
    echo Following files/directories are being copied
    echo \\--------------------------------------------------------------------------------/
    echo "»"
    ls $1
    echo "»"
    checkPrompt
    shift
done
