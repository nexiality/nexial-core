#!/usr/bin/env bash

KEYGEN_HOME=
if [[ -x ${JAVA_HOME}/bin/keytool ]]; then
	KEYGEN_HOME=${JAVA_HOME}/bin
elif [[ -x ${JRE_HOME}/bin/keytool ]]; then
	KEYGEN_HOME=${JRE_HOME}/bin
else
	echo "ERROR!!!"
	echo "Unable to find keytool executable under JAVA_HOME"
	$JAVA -version
	exit 254
fi

rm -f ${USER_NEXIAL_KEYSTORE} > /dev/null 2>&1
${KEYGEN_HOME}/keytool -genkey \
	-keyalg RSA \
	-validity 3650 \
	-keystore ${USER_NEXIAL_KEYSTORE} \
	-storepass "nexialrocks" \
	-keypass "nexialrocks" \
	-alias "default" \
	-dname "CN=127.0.0.1, OU=Nexial, O=Nexial, L=Nexial, S=Nexial, C=Nexiality"
exit $?
