#!/bin/bash

MINIMUM_JAVA_VERSION=17
NEXIAL_JDK_DOWNLOAD_VERSION=${MINIMUM_JAVA_VERSION}
declare -A JDK_DOWNLOAD_URLS
JDK_DOWNLOAD_URLS[Linux_aarch64]=https://download.oracle.com/java/${NEXIAL_JDK_DOWNLOAD_VERSION}/latest/jdk-${NEXIAL_JDK_DOWNLOAD_VERSION}_linux-aarch64_bin.tar.gz
JDK_DOWNLOAD_URLS[Linux_x86_64]=https://download.oracle.com/java/${NEXIAL_JDK_DOWNLOAD_VERSION}/latest/jdk-${NEXIAL_JDK_DOWNLOAD_VERSION}_linux-x64_bin.tar.gz
JDK_DOWNLOAD_URLS[macos_aarch64]=https://download.oracle.com/java/${NEXIAL_JDK_DOWNLOAD_VERSION}/latest/jdk-${NEXIAL_JDK_DOWNLOAD_VERSION}_macos-aarch64_bin.tar.gz
JDK_DOWNLOAD_URLS[macos_x64]=https://download.oracle.com/java/${NEXIAL_JDK_DOWNLOAD_VERSION}/latest/jdk-${NEXIAL_JDK_DOWNLOAD_VERSION}_macos-x64_bin.tar.gz
MSG_ERR_HEADER="=ERROR=========================================================================="
MSG_SUCCESS_HEADER="=SUCCESS========================================================================"
MSG_FOOTER="================================================================================"
MSG_MIN_JAVA="Nexial requires Java ${MINIMUM_JAVA_VERSION} or higher to work."
MSG_SPECIFY_JAVA="If you have installed Java ${MINIMUM_JAVA_VERSION} or higher,"
MSG_SPECIFY_JAVA2="please exit and update the JAVA_HOME environment variable accordingly."
NEXIAL_JAVA_HOME_IMPORT="$HOME/.nexial/.java_home.sh"

function cacheJavaPath() {
		REM is the echo needed, or jus for debugging?
    echo $NEXIAL_JAVA_HOME_IMPORT
    java_path=$1
    echo -e "#!/bin/bash\nexport JAVA=$1" >$NEXIAL_JAVA_HOME_IMPORT
}

function showJavaIncompatibleError() {
    echo
    echo "${MSG_ERR_HEADER}"
    echo "${MSG_MIN_JAVA}"
    echo "The detected Java version is $1."
    echo "${MSG_SPECIFY_JAVA}"
    echo "${MSG_SPECIFY_JAVA2}"
    echo "${MSG_FOOTER}"
    echo
}

function showJavaNotFoundError() {
    echo
    echo "${MSG_ERR_HEADER}"
    echo "${MSG_MIN_JAVA}"
    echo "No Java is detected on the current system."
    echo "${MSG_SPECIFY_JAVA}"
    echo "${MSG_SPECIFY_JAVA2}"
    echo "${MSG_FOOTER}"
    echo
}

function tryDownloadingJava() {
    echo
    NEXIAL_JAVA_HOME=${NEXIAL_HOME}/jdk-${NEXIAL_JDK_DOWNLOAD_VERSION}

    CURRENT_ARCH=$(arch)
    DOWNLOAD_URL_KEY=$(resolveOSName)_${CURRENT_ARCH}

    DOWNLOAD_URL=${JDK_DOWNLOAD_URLS[$DOWNLOAD_URL_KEY]}

    if [[ -z "${DOWNLOAD_URL}" ]]; then
        echo
        echo "${MSG_ERR_HEADER}" 
        echo "Nexial could not download suitable Java for your system (${DOWNLOAD_URL_KEY}). Please install Java ${MINIMUM_JAVA_VERSION}"
        echo "or higher for Nexial to work properly."
        echo "${MSG_FOOTER}"
        echo
        exit 254
    fi

    echo -e "Downloading JDK from $DOWNLOAD_URL\n"

    curl --output /tmp/jdk.tgz "$DOWNLOAD_URL"
    mkdir -p "$NEXIAL_JAVA_HOME" 
    rm -rf "$NEXIAL_JAVA_HOME/*" 2> /dev/null
    tar --extract --file /tmp/jdk.tgz --directory "$NEXIAL_JAVA_HOME" --strip-components 1

    echo
    echo "${MSG_SUCCESS_HEADER}"
    echo
    echo "Nexial installed the Java '$("$NEXIAL_JAVA_HOME/bin/java" -version 2>&1 | awk -F '"' '/version/ {print $2}')' successfully".
    echo "${MSG_FOOTER}"

    cacheJavaPath "$NEXIAL_JAVA_HOME/bin/java"
}

function promptForCustomJdkLocation() {
    echo
    read -p "Enter your custom JAVA_HOME location: " USER_TYPED_JAVA_HOME
    if [[ -z "${USER_TYPED_JAVA_HOME}" ]]; then
        printf "Error: Invalid JAVA_HOME location.\nPlease enter a valid JAVA_HOME location (or use 'CTRL + C' to exit.)"
        promptForCustomJdkLocation
    else
        USER_PROVIDED_JAVA="${USER_TYPED_JAVA_HOME}/bin/java"
        if [[ -x "${USER_PROVIDED_JAVA}" ]]; then
            JAVA=$USER_PROVIDED_JAVA
            cacheJavaPath $USER_PROVIDED_JAVA
        else
            echo "Error: No valid java executable binary found at $USER_PROVIDED_JAVA"
            echo "Please enter a valid JAVA_HOME location (or use 'CTRL + C' to exit.)"
            promptForCustomJdkLocation
        fi
    fi
}

function optToInstallJava() {
    MENU="Select one of the following options:\n"
    PROVIDE_CUSTOM_JDK_MSG="Specify the location of Java that is version ${MINIMUM_JAVA_VERSION} or higher"
    LET_NEXIAL_DOWNLOAD_JDK_MSG="Let Nexial install a compatible Java for you"
    QUIT_MSG="Exit"

    echo -e $MENU

    PS3='
Enter your selection (1, 2, or 3) and press Enter: '

    options=("$PROVIDE_CUSTOM_JDK_MSG" "$LET_NEXIAL_DOWNLOAD_JDK_MSG" "$QUIT_MSG")
    select opt in "${options[@]}"; do
    case $REPLY in
    1)
        promptForCustomJdkLocation
        break
        ;;
    2)
        tryDownloadingJava
        break
        ;;
    3)
        printf "\nPlease install Java %s or higher manually for Nexial to work.\nThanks for trying Nexial!\n" ${MINIMUM_JAVA_VERSION}
        exit 254
        ;;
        *) echo "Invalid selection $REPLY. Please enter your selection (1, 2, or 3) and press Enter." ;;
        esac
    done
    echo
}

function resolveJavaFromWhichCommand() {
    JAVA=$(which java)
    if [[ -z "$JAVA" ]]; then
        showJavaNotFoundError
        optToInstallJava
        checkJava
    else
        echo "resolving Java as ${JAVA}"
        cacheJavaPath $JAVA
    fi
}
