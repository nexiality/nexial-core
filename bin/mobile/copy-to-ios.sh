#!/usr/bin/env bash

# -----------------------------------------------------------------------------------------------
# Copy local file to connected iOS device
# -----------------------------------------------------------------------------------------------

if [[ "$1" = "" ]]; then
    echo
    echo "ERROR: No source file specified!"
    echo "USAGE: $0 [source file]"
    echo
    exit 254
fi

echo
if whereis xcrun > /dev/null 2>&1 ; then
	xcrun simctl addmedia booted "$1"
	exit $?
else
	echo "ERROR: Required utility 'xcrun' not found; Unable to proceed"
	exit 253
fi
