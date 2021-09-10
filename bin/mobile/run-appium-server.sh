#!/usr/bin/env bash

# -----------------------------------------------------------------------------------------------
# Start Appium server from either Appium desktop (APPIUM_DESKTOP_HOME) or global node_module
# Appium (APPIUM_HOME)
# -----------------------------------------------------------------------------------------------

function printAppiumDesktopMissing() {
  echo "Please be sure to install Appium Desktop, and set its installation directory to"
  echo "the APPIUM_DESKTOP_HOME environment variable."
  echo
  echo
}

function isMacOS() {
  unameOut="$(uname -s)"
  if  [[ "$unameOut" == "Darwin" ]]; then
    echo  true
  else
    echo  false
  fi
}

# runtime options
# https://appium.io/docs/en/writing-running-appium/server-args/
if [[ "$1" == "" ]]; then
  APPIUM_IP=127.0.0.1
else
  APPIUM_IP=$1
fi

if [[ "$APPIUM_LOG" == "" ]]; then
  if [[ "$TMPDIR" == "" ]]; then
    APPIUM_LOG=/tmp/appium.log
  else
    APPIUM_LOG=${TMPDIR}appium.log
  fi
fi

APPIUM_OPT="--allow-cors --session-override --log $APPIUM_LOG --log-level info:info --log-timestamp\
 --local-timezone --no-perms-check --debug-log-spacing --relaxed-security -a $APPIUM_IP $APPIUM_OPT"

echo
echo "export APPIUM_LOG=[location of log file]"
echo "Usage: $0 [optional:listening IP]"
echo "Ctrl-C to stop"
echo

# favors global appium node module over appium desktop installation
which appium > /dev/null 2>&1
if [[ "$?" == "0" ]]; then
  echo "Running appium server via appium.exe"
  appium $APPIUM_OPT
  exit $?
fi

# maybe appium module is installed?
APPIUM_SCRIPT=/usr/local/lib/node_modules/appium/build/lib/main.js
if [[ -f $APPIUM_SCRIPT ]]; then
  echo "Running appium server via $APPIUM_SCRIPT"
  node $APPIUM_SCRIPT $APPIUM_OPT
  exit $?
fi

APPIUM_SCRIPT=~/node_modules/appium/build/lib/main.js
if [[ -f $APPIUM_SCRIPT ]]; then
  echo "Running appium server via $APPIUM_SCRIPT"
  node $APPIUM_SCRIPT $APPIUM_OPT
  exit $?
fi

# maybe appium desktop is installed?
isMac=$(isMacOS)
# can't find appium.exe, let's try appium desktop instead
if [[ "$APPIUM_DESKTOP_HOME" != "" ]]; then
  if [[ ! -d "$APPIUM_DESKTOP_HOME" ]]; then
    echo "ERROR!!! Invalid APPIUM_DESKTOP_HOME specified."
    printAppiumDesktopMissing
    exit 254
  fi
else
  if [[ $isMac ]]; then
    # Mac
    APPIUM_DESKTOP_HOME=/Applications/Appium.app
    if [[ ! -d "$APPIUM_DESKTOP_HOME" ]]; then
      echo "ERROR!!! Appium Desktop is not installed or not found in $APPIUM_DESKTOP_HOME"
      printAppiumDesktopMissing
      exit 254
    fi
  else
    echo "ERROR!!! Unable to resolve path to Appium Desktop. Perhaps it isn't installed?"
    printAppiumDesktopMissing
    exit 254
  fi
fi

if [[ $isMac ]]; then
  APPIUM_SCRIPT=$APPIUM_DESKTOP_HOME/Contents/Resources/app/node_modules/appium/build/lib/main.js
else
  APPIUM_SCRIPT=$APPIUM_DESKTOP_HOME/resources/app/node_modules/appium/build/lib/main.js
fi

if [[ ! -f "$APPIUM_SCRIPT" ]]; then
  echo "ERROR!!! Invalid Appium script at $APPIUM_SCRIPT"
  printAppiumDesktopMissing
  exit 254
fi

echo "Running appium server script from $APPIUM_SCRIPT"
node $APPIUM_SCRIPT $APPIUM_OPT
