-- nexial:vouchers
SELECT
	V.ID1    AS "V.ID",
	V.ID2    AS "C.ID",
	ID3      AS "E,ID",
	DATE1    AS "C,Date",
	DATE2    AS "P,Date",
	V.AMT1   AS "G.Amount",
	AMT2     AS "N.Amount",
	T.AMT1   AS "T.Amount",
	V.LOG_ID AS "L.ID"
FROM
	DATA_TABLE1 V, DATA_TABLE2 T
WHERE V.ID1 = T.ID1
      AND V.YEAR = ${TAX Year}
      AND V.ID2 = ${Client ID}
      AND V.ID1 = ${Voucher ID};

-- nexial:processing log
SELECT
	BATCHID        AS "B.ID",
	COMMENT1       AS "Comment",
	NEW_COUNT      AS "New Count",
	UPDATED_COUNT  AS "Updatd Count",
	NEW2_COUNT     AS "New2 Count",
	UPDATED2_COUNT AS "Updated2 Count"
FROM
	PROCESSINGLOG L
WHERE L.LOG_ID = ${vouchers}.data[0].[Log ID]
      AND (NEW_COUNT + UPDATED_COUNT + NEW2_COUNT + UPDATED2_COUNT) <= ${vouchers}.rowCount;
