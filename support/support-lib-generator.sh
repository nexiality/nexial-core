#!/bin/bash
#generated nexial-lib-x.x.zip in build/distributions.
cd ..
gradle clean generateNexialLib
build_ret=$?
if [[ ${build_ret} != 0 ]] ; then
    echo nexial-lib generation failed!
    exit ${build_ret}
fi
echo nexial-lib generated successfully.