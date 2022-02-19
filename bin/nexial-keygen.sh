#!/bin/bash

# ---------------------------------------------------------------------------
# [nexial-keygen]
# An use-only utility to generate nexial-only JKS cert for execution host.
# Generated cert will be stored in %NEXIAL_USER_HOME%
# ---------------------------------------------------------------------------

NEXIAL_HOME=$(cd `dirname $0`/..; pwd -P)
. ${NEXIAL_HOME}/bin/.commons.sh
title "nexial certificate generator"
checkJava
resolveEnv

# run now
echo
. ${NEXIAL_HOME}/bin/.keygen.sh
exit $?
