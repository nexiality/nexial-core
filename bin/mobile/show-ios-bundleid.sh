#!/usr/bin/env bash

# -----------------------------------------------------------------------------------------------
# Show bundleId for the specified .app file
# -----------------------------------------------------------------------------------------------

if [[ "$1" = "" ]]; then
    echo
    echo "ERROR: No .app file specified!"
    echo "USAGE: $0 [location of .app file]"
    echo
    exit 254
fi

echo
if whereis osascript > /dev/null 2>&1 ; then
	appPath=$(dirname "$1/.")
	echo "[$(basename $0)]"
	echo "app:			$appPath"
	echo "bundleId:	$(osascript -e 'id of app "'"$appPath"'"')"
	echo
else
	echo "Required utility 'osascript' not found; Unable to proceed"
	echo
	exit 253
fi
