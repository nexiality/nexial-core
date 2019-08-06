#!/bin/bash

# --------------------------------------------------------------------------------
# environment variable guide
# --------------------------------------------------------------------------------
# JAVA_HOME           - home directory of a valid JDK installation (1.6 or above)
# --------------------------------------------------------------------------------

function resolveOSXAppPath() {
    local appExec=$1
    local me=`whoami`
    local path=""

    IFS=$'\n'

    for p in `locate "MacOS/${appExec}" | grep -E "${appExec}$"`; do
        if [[ "$path" = "" ]]; then
            # first time.. just use what we found
            path="${p}"
        else
            if [[ "${p}" = *"${me}"* ]]; then
                # the one under `me` takes a higher precedence
                path="${p}"
            fi
        fi
    done

    unset IFS

    echo "${path}"
}


function resolveLinuxAppPath() {
    location=`which $1`
    if [[ "$?" = "0" ]]; then
        echo "${location}"
    else
        echo ""
    fi
}


function checkJava() {
    if [[ "${JAVA_HOME}" = "" && "${JRE_HOME}" = "" ]]; then
        echo
        echo =WARNING=========================================================================
        echo Neither the JAVA_HOME nor the JRE_HOME environment variable is defined.
        echo Nexial will use the JVM based on current PATH. Nexial requires Java 1.8
        echo or above to run, so this might not work...
        echo ================================================================================
        JAVA=`which java`
        echo resolving Java as ${JAVA}
        echo
    else
        if [[ -x ${JAVA_HOME}/bin/java ]]; then
            JAVA=${JAVA_HOME}/bin/java
        else
            if [[ -x ${JRE_HOME}/bin/java ]]; then
                JAVA=${JRE_HOME}/bin/java
            else
                echo =WARNING=========================================================================
                echo JAVA_HOME nor JRE_HOME environment variable is not defined correctly.
                echo One of these environment variables is needed to run this program.
                echo NB: JAVA_HOME should point to a JDK not a JRE
                echo
                echo Nexial will use the JVM based on current PATH. Nexial requires Java 1.8
                echo or above to run, so this might not work...
                echo ================================================================================
                JAVA=`which java`
                echo resolving Java as ${JAVA}
                echo
            fi
        fi
    fi
}


function title() {
    title="${1}"
    title_length=${#title}
    space_length=$((80 - 4 - ${title_length} - 3))

    echo
    echo "--------------------------------------------------------------------------------"
    echo "|                        nexial - test automation for all                      |"
    echo "--------------------------------------------------------------------------------"
    printf "[:: "
    printf "${title}"
    printf "%0.s " $(seq 1 ${space_length})
    printf "::]"
    echo
    echo "--------------------------------------------------------------------------------"
}


export NEXIAL_OS=
function resolveEnv() {
    unameOut="$(uname -s)"
    case "${unameOut}" in
        Linux*)     export NEXIAL_OS=Linux;;
        Darwin*)    export NEXIAL_OS=Mac;;
        CYGWIN*)    export NEXIAL_OS=Cygwin;;
        MINGW*)     export NEXIAL_OS=MinGw;;
        *)          export NEXIAL_OS="UNKNOWN:${unameOut}"
    esac

    JAVA_VERSION=`echo "$(${JAVA} -version 2>&1)" | grep "java version" | awk '{ print substr($3, 2, length($3)-2); }'`

    echo "» ENVIRONMENT: "
    echo "  CURRENT TIME:   `date \"+%Y-%m-%d %H:%M%:%S\"`"
    echo "  CURRENT USER:   ${USER}"
    echo "  CURRENT HOST:   `hostname`"
    echo "  JAVA:           ${JAVA}"
    echo "  JAVA VERSION:   ${JAVA_VERSION}"
    echo "  NEXIAL_HOME:    ${NEXIAL_HOME}"
    echo "  NEXIAL_LIB:     ${NEXIAL_LIB}"
    echo "  NEXIAL_CLASSES: ${NEXIAL_CLASSES}"
    echo "  PROJECT_BASE:   ${PROJECT_BASE}"
    if [[ "${PROJECT_HOME}" != "" ]]; then
        echo "  PROJECT_HOME:   ${PROJECT_HOME}"
    fi
    echo
}


function resolveAppPath() {
    local cacheKey="$1"

    if [[ ! -f "${CACHE_FILE}" ]] ; then
        touch "${CACHE_FILE}"
    fi

    local appPath=` cat "${CACHE_FILE}" | grep "$1=" | sed "s/$1=//g" `

    if [[ "${appPath}" = "" || ! -f "${appPath}" ]]; then
        # re-search for app path
        if [[ "${IS_MAC}" = "true" ]]; then
            sed -E -i '' "/$1=/d" "${CACHE_FILE}"
            appPath="`resolveOSXAppPath "$1"`"
        else
            sed -i "/$1=/d" "${CACHE_FILE}"
            appPath="`resolveLinuxAppPath "$1"`"
        fi

        echo "$1=${appPath}" >> "${CACHE_FILE}"
    fi

    echo ${appPath}
}


# utilities to be invoked by other frontend scripts
export PROJECT_BASE=~/projects
export NEXIAL_HOME=$(cd `dirname $0`/..; pwd -P)
export NEXIAL_LIB=${NEXIAL_HOME}/lib
export NEXIAL_CLASSES=${NEXIAL_HOME}/classes
export IS_MAC=$([[ "`uname -s`" = "Darwin" ]] && echo "true" || echo "false" )
export CACHE_FILE=$([[ ${IS_MAC} = "true" ]] && echo "$HOME/.nexial/cache.macos" || echo "$HOME/.nexial/cache.nix" )

CHROME_KEY=$([[ ${IS_MAC} = "true" ]] && echo "Google Chrome" || echo "google-chrome" )
export DEFAULT_CHROME_BIN="`resolveAppPath "${CHROME_KEY}"`"
if [[ -z "${DEFAULT_CHROME_BIN//}" ]]; then
    echo "WARNING: Unable to resolve location of Google Chrome. If you want to use Chrome,"
    echo "         you will need to set its location via the CHROME_BIN environment variable"
    echo
    DEFAULT_CHROME_BIN=
fi

#FIREFOX_KEY=$([[ ${IS_MAC} = "true" ]] && echo "firefox-bin" || echo "firefox-bin" )
FIREFOX_KEY=firefox-bin
export DEFAULT_FIREFOX_BIN="`resolveAppPath "${FIREFOX_KEY}"`"
if [[ -z "${DEFAULT_FIREFOX_BIN//}" ]]; then
    echo "WARNING: Unable to resolve location of Firefox. If you want to use Firefox, you "
    echo "         will need to set its location via the FIREFOX_BIN environment variable"
    echo
    DEFAULT_FIREFOX_BIN=
fi


# --------------------------------------------------------------------------------
# setting Java runtime options and classpath
# --------------------------------------------------------------------------------
JAVA_OPT="${JAVA_OPT} -ea"
JAVA_OPT="${JAVA_OPT} -Xss24m"
JAVA_OPT="${JAVA_OPT} -Dfile.encoding=UTF-8"
# JAVA_OPT="${JAVA_OPT} -Djava.awt.headless=true"
JAVA_OPT="${JAVA_OPT} -Dnexial.home=${NEXIAL_HOME}"
JAVA_OPT="${JAVA_OPT} -Dwebdriver.winium.verbose=true"
JAVA_OPT="${JAVA_OPT} -Dwebdriver.winium.silent=false"
# JAVA_OPT="${JAVA_OPT} -Dwebdriver.winium.logpath=$TMPDIR/winium-service.log"
JAVA_OPT="${JAVA_OPT} -Dorg.apache.poi.util.POILogger=org.apache.poi.util.NullLogger"