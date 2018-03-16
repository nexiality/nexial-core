-- nexial:${code} result
SELECT OFFICELOCATIONDESC AS "description", ADDRESSLINE1 || ' ' || ADDRESSLINE2 || ', ' || CITY || ' ' || OFFICELOCATIONSTATE || ' ' || ZIP || ' ' || COUNTRY AS "fullAddress" FROM OFFICELOCATIONS WHERE OFFICELOCATIONCODE = '${code}';

