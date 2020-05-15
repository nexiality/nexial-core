#!/bin/sh

# -----------------------------------------------------------------------------
# set up
# -----------------------------------------------------------------------------
# environment variable to override:
#   GITHUB_TOKEN: required
#   GITHUB_PROJECT: default nexiality/nexial-core
#   PROJECT_NAME: default nexial-core

if [[ -v ${GITHUB_TOKEN} ]]; then
    echo ** MISSING REQUIRED ENVIRONMENT VARIABLE
    echo ** ${GITHUB_TOKEN}
    echo **
    echo ** UNABLE TO CONTINUE, EXITING...
    exit -1
fi

GITHUB_REPO=${GITHUB_PROJECT:-nexiality/nexial-core}
NEXIAL_HOME=${WORKSPACE}
NEXIAL_DIST_HOME=${WORKSPACE}/build/install/${PROJECT_NAME:-nexial-core}
TARGET_VERSION=v`cat ${NEXIAL_HOME}/support/target_version.txt`
RELEASE_DATE=`date +%Y/%m/%d`
RELEASE_TAG=nexial-core-${TARGET_VERSION}_`printf %04d ${BUILD_NUMBER}`
RELEASE_ASSET=nexial-core-${RELEASE_VERSION:-dev}_`printf %04d ${BUILD_NUMBER}`.zip

IS_PRERELEASE="false"
if [ -z $RELEASE_VERSION ]; then
    IS_PRERELEASE="true"
fi
echo Release ${NEXIAL_DIST_HOME}/$RELEASE_ASSET as prelease - ${IS_PRERELEASE}

RELEASE_DESCRIPTION="Release ${RELEASE_TAG} on ${RELEASE_DATE}\\n- [Release Notes](https://nexiality.github.io/documentation/release/nexial-core-${TARGET_VERSION}.changelog)"

# -----------------------------------------------------------------------------
# copy over support scripts
# -----------------------------------------------------------------------------
mkdir ${NEXIAL_DIST_HOME}/support
cp -fR ${NEXIAL_HOME}/support/nexial* ${NEXIAL_DIST_HOME}/support

# run nexial-command-generator
echo Generating command listing...
cd ${NEXIAL_DIST_HOME}/support
chmod -fR 755 *.sh
./nexial-command-generator.sh

build_ret=$?
if [ ${build_ret} != 0 ]; then
    echo command generator failed!
    exit ${build_ret}
fi

cd ..
rm -frv support

# -----------------------------------------------------------------------------
# run nexial-script-update
# -----------------------------------------------------------------------------
echo Update Nexial templates with latest command listing
cd bin
./nexial-script-update.sh -v -t ../template

build_ret=$?
if [ ${build_ret} != 0 ]; then
    echo script update failed!
    exit ${build_ret}
fi

# -----------------------------------------------------------------------------
# zip for distribution
# -----------------------------------------------------------------------------
echo creating distribution ${NEXIAL_DIST_HOME}/${RELEASE_ASSET}
cd ${NEXIAL_DIST_HOME}
zip -9 -r ${NEXIAL_DIST_HOME}/${RELEASE_ASSET} *

# -----------------------------------------------------------------------------
# create a release
# -----------------------------------------------------------------------------
echo "Publishing to Github..."
echo creating a release ${RELEASE_TAG}
cd ${NEXIAL_DIST_HOME}
release=$(curl -XPOST -H "Authorization:token ${GITHUB_TOKEN}" --data "{\"tag_name\": \"$RELEASE_TAG\", \"target_commitish\": \"master\", \"name\": \"${RELEASE_TAG}\", \"body\": \"$RELEASE_DESCRIPTION\", \"draft\": false, \"prerelease\": ${IS_PRERELEASE}}" https://api.github.com/repos/${GITHUB_REPO}/releases)

echo ${release}

# Extract the id of the release from the creation response
id=$(echo "$release" | sed -n -e 's/"id":\ \([0-9]\+\),/\1/p' | head -n 1 | sed 's/[[:blank:]]//g')

# Upload the artifact
echo uploading distribution ${NEXIAL_DIST_HOME}/${RELEASE_ASSET}
curl -XPOST -H "Authorization:token ${GITHUB_TOKEN}" -H "Content-Type:application/octet-stream" --data-binary @${NEXIAL_DIST_HOME}/${RELEASE_ASSET} https://uploads.github.com/repos/${GITHUB_REPO}/releases/$id/assets?name=${RELEASE_ASSET}

# done!