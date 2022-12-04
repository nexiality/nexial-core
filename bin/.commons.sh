#!/bin/bash

# --------------------------------------------------------------------------------
# environment variable guide
# --------------------------------------------------------------------------------
# JAVA_HOME           - home directory of a valid JDK installation (${MINIMUM_JAVA_VERSION} or above)
# --------------------------------------------------------------------------------
. ${NEXIAL_HOME}/bin/.java_check_and_install.sh

MSG_WARNING_HEADER="=WARNING========================================================================"
MSG_FOOTER="================================================================================"

function resolveOSXAppPath() {
    local appExec=$1
    local me=`whoami`
    local path=""

    IFS=$'\n'

    # todo: need alternative from `locate` since it requires local sudo rights.
    for p in $(locate "MacOS/${appExec}" | grep -E "${appExec}$"); do
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
    location=$(which $1)
    if [[ "$?" = "0" ]]; then
        echo "${location}"
    else
        echo ""
    fi
}


function resolveOSName() {
    local NEXIAL_OS=
    	unameOut="$(uname -s)"
    case "${unameOut}" in
    	Linux*) NEXIAL_OS=Linux ;;
    	Darwin*) NEXIAL_OS=Mac ;;
    	CYGWIN*) NEXIAL_OS=Cygwin ;;
    	MINGW*) NEXIAL_OS=MinGw ;;
    	*) NEXIAL_OS="UNKNOWN:${unameOut}" ;;
    esac
    	echo $NEXIAL_OS
}

export NEXIAL_OS=$(resolveOSName)


function checkJava() {
    # Priority 1: Import from cached JDK location
    if [[ -f "${NEXIAL_JAVA_HOME_IMPORT}" ]]; then
        . ${NEXIAL_JAVA_HOME_IMPORT}
        if [[ ! ((-x "${JAVA}")) ]]; then
            JAVA=
        fi
    fi

    # Priority 2: Try to find system installed java
    if [[ -z "${JAVA}" ]]; then
        if [[ "${JAVA_HOME}" = "" && "${JRE_HOME}" = "" ]]; then
            echo
            echo "${MSG_WARNING_HEADER}"
            echo "Neither the JAVA_HOME nor the JRE_HOME environment variable is defined."
            echo "Nexial will use the JVM based on current PATH. Nexial requires Java $MINIMUM_JAVA_VERSION"
            echo "or above to run, so this might not work..."
            echo "${MSG_FOOTER}"
            resolveJavaFromWhichCommand
        else
            if [[ -x ${JAVA_HOME}/bin/java ]]; then
                JAVA=${JAVA_HOME}/bin/java
            else
                if [[ -x ${JRE_HOME}/bin/java ]]; then
                    JAVA=${JRE_HOME}/bin/java
                else
                    echo "${MSG_WARNING_HEADER}"
                    echo "JAVA_HOME nor JRE_HOME environment variable is not defined correctly."
                    echo "One of these environment variables is needed to run this program."
                    echo "NB: JAVA_HOME should point to a JDK not a JRE"
                    echo
                    echo "Nexial will use the JVM based on current PATH. Nexial requires Java $MINIMUM_JAVA_VERSION"
                    echo "or above to run, so this might not work..."
                    echo "${MSG_FOOTER}"
                    resolveJavaFromWhichCommand
                fi
            fi
        fi
    fi

    java_local_version=$("$JAVA" -version 2>&1 | awk -F '"' '/version/ {print $2}')
    java_version=$java_local_version
    JAVA_SUPPORTS_MODULE="true"

    IFS='.'
    read -ra JDK_VERSION_ARRAY <<<"$java_local_version"
    JAVA_VERSION=${JDK_VERSION_ARRAY[0]}
    IFS=' ' # Reset the split character to default

		if [[ "$JAVA_VERSION" -lt "$MINIMUM_JAVA_VERSION" ]]; then
			showJavaIncompatibleError $java_local_version
			optToInstallJava
			checkJava
		fi

		if [[ "$JAVA_SUPPORTS_MODULE" = "true" ]]; then
				JAVA_OPT="${JAVA_OPT} --add-opens java.base/java.lang=ALL-UNNAMED"
				JAVA_OPT="${JAVA_OPT} --add-opens java.base/java.lang.reflect=ALL-UNNAMED"
				JAVA_OPT="${JAVA_OPT} --add-opens java.base/java.io=ALL-UNNAMED"
				JAVA_OPT="${JAVA_OPT} --add-opens java.base/java.util=ALL-UNNAMED"
				JAVA_OPT="${JAVA_OPT} --add-opens java.base/java.text=ALL-UNNAMED"
				JAVA_OPT="${JAVA_OPT} --add-opens java.desktop/java.awt.font=ALL-UNNAMED"
		fi
}


function title() {
    title="${1}"
    title_length=${#title}
    space_length=$((80 - 4 - ${title_length} - 3))

    echo
    echo "--------------------------------------------------------------------------------"
    echo "|                       nexial - automation for everyone!                      |"
    echo "--------------------------------------------------------------------------------"
    printf "[:: "
    printf "${title}"
    printf "%0.s " $(seq 1 ${space_length})
    printf "::]"
    echo
    echo "--------------------------------------------------------------------------------"
}

function resolveEnv() {

		NEXIAL_VERSION=$(cat ${NEXIAL_HOME}/version.txt)

    echo "» ENVIRONMENT: "
    echo "  CURRENT TIME:     $(date "+%Y-%m-%d %H:%M:%S")"
    echo "  CURRENT USER:     ${USER}"
    echo "  CURRENT HOST:     $(hostname)"
    echo "  JAVA:             ${JAVA}"
    echo "  JAVA VERSION:     ${JAVA_VERSION}"
    echo "  NEXIAL_VERSION:   ${NEXIAL_VERSION}"
    echo "  NEXIAL_HOME:      ${NEXIAL_HOME}"
    echo "  NEXIAL_LIB:       ${NEXIAL_LIB}"
    echo "  NEXIAL_CLASSES:   ${NEXIAL_CLASSES}"
    echo "  PROJECT_BASE:     ${PROJECT_BASE}"
    if [[ "${PROJECT_HOME}" != "" ]]; then
        echo "  PROJECT_HOME:     ${PROJECT_HOME}"
    fi
    echo "  USER_NEXIAL_HOME: ${USER_NEXIAL_HOME}"
    echo
}


function resolveAppPath() {
    local cacheKey="$1"

    if [[ ! -f "${CACHE_FILE}" ]] ; then
        touch "${CACHE_FILE}"
    fi

    local appPath=$(cat "${CACHE_FILE}" | grep "$1=" | sed "s/$1=//g")

    if [[ "${appPath}" = "" || ! -f "${appPath}" ]]; then
        # re-search for app path
        if [[ "${IS_MAC}" = "true" ]]; then
            sed -E -i '' "/$1=/d" "${CACHE_FILE}"
            appPath="$(resolveOSXAppPath "$1")"
        else
            sed -i "/$1=/d" "${CACHE_FILE}"
            appPath="$(resolveLinuxAppPath "$1")"
        fi

        echo "$1=${appPath}" >>"${CACHE_FILE}"
    fi

    echo ${appPath}
}


# utilities to be invoked by other frontend scripts
export USER_NEXIAL_HOME="$HOME/.nexial"
mkdir -p ${USER_NEXIAL_HOME}
export PROJECT_BASE=~/projects
export NEXIAL_HOME=$(cd $(dirname $0)/..; pwd -P)
export NEXIAL_LIB=${NEXIAL_HOME}/lib
export NEXIAL_CLASSES=${NEXIAL_HOME}/classes
export USER_NEXIAL_LIB=${USER_NEXIAL_HOME}/lib
export USER_NEXIAL_JAR=${USER_NEXIAL_HOME}/jar
export USER_NEXIAL_DLL=${USER_NEXIAL_HOME}/dll
export USER_NEXIAL_INSTALL=${USER_NEXIAL_HOME}/install
export USER_NEXIAL_KEYSTORE=${USER_NEXIAL_HOME}/nexial-keystore.jks

export IS_MAC=$([[ "$(uname -s)" = "Darwin" ]] && echo "true" || echo "false")
export CACHE_FILE=$([[ ${IS_MAC} = "true" ]] && echo "${USER_NEXIAL_HOME}/cache.macos" || echo "${USER_NEXIAL_HOME}/cache.nix")

CHROME_KEY=$([[ ${IS_MAC} = "true" ]] && echo "Google Chrome" || echo "google-chrome")
export DEFAULT_CHROME_BIN="$(resolveAppPath "${CHROME_KEY}")"
if [[ -z "${DEFAULT_CHROME_BIN///}" ]]; then
    echo "WARNING: Unable to resolve location of Google Chrome. If you want to use Chrome,"
    echo "         you will need to set its location via the CHROME_BIN environment variable"
    echo
    DEFAULT_CHROME_BIN=
fi

# android sdk
if [[ ! -f "${ANDROID_HOME}" ]] ; then ANDROID_HOME=${USER_NEXIAL_HOME}/android/sdk ; fi
if [[ ! -f "${ANDROID_SDK_ROOT}" ]] ; then ANDROID_SDK_ROOT=${USER_NEXIAL_HOME}/android/sdk ; fi

# javaui/jubula
if [[ ! -f "${JUBULA_HOME}" ]] ; then JUBULA_HOME=/Application/jubula_8.8.0.034 ; fi

FIREFOX_KEY=firefox-bin
export DEFAULT_FIREFOX_BIN="$(resolveAppPath "${FIREFOX_KEY}")"
if [[ -z "${DEFAULT_FIREFOX_BIN///}" ]]; then
    echo "WARNING: Unable to resolve location of Firefox. If you want to use Firefox, you "
    echo "         will need to set its location via the FIREFOX_BIN environment variable"
    echo
    DEFAULT_FIREFOX_BIN=
fi


# --------------------------------------------------------------------------------
# setting Java runtime options and classpath
# --------------------------------------------------------------------------------
mkdir $HOME/tmp > /dev/null 2>&1

JAVA_OPT="${JAVA_OPT} -ea"
JAVA_OPT="${JAVA_OPT} -Xss24m"
JAVA_OPT="${JAVA_OPT} -Djava.io.tmpdir=$HOME/tmp"
JAVA_OPT="${JAVA_OPT} -Dfile.encoding=UTF-8"
# JAVA_OPT="${JAVA_OPT} -Djava.awt.headless=true"
JAVA_OPT="${JAVA_OPT} -Dnexial.home=${NEXIAL_HOME}"
JAVA_OPT="${JAVA_OPT} -Dwebdriver.winium.verbose=true"
JAVA_OPT="${JAVA_OPT} -Dwebdriver.winium.silent=false"
# JAVA_OPT="${JAVA_OPT} -Dwebdriver.winium.logpath=$TMPDIR/winium-service.log"
JAVA_OPT="${JAVA_OPT} -Dorg.apache.poi.util.POILogger=org.apache.poi.util.NullLogger"

# remove erroneous setup.jar in .nexial/lib
if [[ -f "$USER_NEXIAL_LIB/setup.jar" ]] ; then
	rm -f "$USER_NEXIAL_LIB/setup.jar" > /dev/null 2>&1 
fi
