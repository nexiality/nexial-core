package org.nexial.core.plugins.bai2

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.StringUtils.startsWith
import org.nexial.core.plugins.bai2.BaiConstants.ACCOUNT
import org.nexial.core.plugins.bai2.BaiConstants.ACCOUNT_HEADER
import org.nexial.core.plugins.bai2.BaiConstants.ACCOUNT_IDENTIFIER_CODE
import org.nexial.core.plugins.bai2.BaiConstants.ACCOUNT_TRAILER
import org.nexial.core.plugins.bai2.BaiConstants.TRANSACTION
import org.nexial.core.plugins.bai2.BaiConstants.accountHeaders
import org.nexial.core.plugins.bai2.BaiConstants.accountTrailerFields
import org.nexial.core.plugins.bai2.BaiConstants.fieldDelim
import org.nexial.core.plugins.bai2.BaiConstants.recordDelim
import org.nexial.core.plugins.bai2.BaiConstants.transactionFields
import org.nexial.core.plugins.bai2.BaiFile.Companion.isNumeric
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.variable.TextDataType
import java.util.*
import kotlin.collections.ArrayList

class BaiAccount : BaiModel {

    override fun field(recordType: String, name: String): TextDataType {
        return when (recordType) {
            ACCOUNT_HEADER -> TextDataType(header!!.get(name))
            ACCOUNT_TRAILER -> TextDataType(trailer!!.get(name))
            else -> {
                val builder = StringBuilder()
                var value: String
                records.forEach({ transaction ->
                    value = transaction.field(recordType, name).textValue
                    builder.append(value).append(",")
                })
                val newValue = builder.toString().removeSuffix(",")
                TextDataType(newValue)
            }
        }
    }

    private var queue: Queue<String> = ArrayDeque<String>()

    constructor()

    constructor(queue: Queue<String>) {
        this.queue = queue
        parse()
    }

    override fun parse(): BaiModel {
        parseAccountIdentifier()
        delegate()
        parseAccountTrailer()
        return this
    }

    private fun parseAccountIdentifier() {
        val nextRecord = queue.peek()
        if (startsWith(nextRecord, ACCOUNT_IDENTIFIER_CODE)) {
            this.header = BaiAccountIdentifier(nextRecord)
            errors.addAll((this.header as BaiAccountIdentifier).validate())
            queue.poll()
        }
    }

    private fun delegate() {
        while (true) {
            val nextRecord = queue.peek()
            if (StringUtils.startsWith(nextRecord, BaiConstants.TRANSACTION_CODE)) {
                val transaction = BaiTransaction(queue.peek()).parse()
                this.records.add(transaction)
                errors.addAll(transaction.errors)
                queue.poll()
            } else {
                break
            }
        }
    }


    private fun parseAccountTrailer() {
        val nextRecord = queue.peek()
        if (StringUtils.startsWith(nextRecord, BaiConstants.ACCOUNT_TRAILER_CODE)) {
            this.trailer = BaiAccountTrailer(nextRecord)
            errors.addAll((this.trailer as BaiAccountTrailer).validate())
            queue.poll()
        }
    }

    override fun filter(recordType: String, condition: String): BaiModel? {
        if (recordType == ACCOUNT) {
            val options = condition.split("=")
            val fieldName: String = options[0].trim()
            val fieldValue: String = options[1].trim()
            val value = (header as BaiAccountIdentifier).accountHeaderMap[fieldName]
            return if (fieldValue == value) {
                ConsoleUtils.log("matched account found")
                this
            } else null
        }else {
            val matchedTransactions: MutableList<BaiModel> = ArrayList()
            val baiAccount = BaiAccount()
            records.forEach({ transaction ->
                val newTransaction = transaction.filter(recordType = recordType, condition = condition)
                if (newTransaction != null) {
                    matchedTransactions.add(newTransaction)
                }
            })

            if (CollectionUtils.isNotEmpty(matchedTransactions)) {
                baiAccount.records = matchedTransactions
                return baiAccount
            }
            return baiAccount
        }
    }

    override fun toString(): String {
        val transactionsString = StringBuilder()
        records.forEach({ transaction -> transactionsString.append(transaction.toString()) })
        return "$header$transactionsString$trailer".replace("null", "")
    }
}

data class BaiAccountIdentifier(private val nextRecord: String) : Header() {
    override fun get(fieldName: String) = accountHeaderMap.getValue(fieldName)

    var accountHeaderMap: Map<String, String> = mutableMapOf()

    init {
        // todo: parsing strategy to support multiple type codes and composite funds type

        /*
        Type 03 records may report several different status and/or summary amounts for the same account. For example, a single 03
        record might report ledger balance and available balance, as well as the amount, item count and funds type for total credits and
        total debits. The “Type Code,” “Amount,” “Item Count” and “Funds Type” fields are repeated to identify each status or summary
        type.
        */

        val values: Array<String> = StringUtils.splitByWholeSeparatorPreserveAllTokens(StringUtils.removeEnd(nextRecord, recordDelim).trim(), fieldDelim)
        // todo: implement 88 Continuation lookup
        if (accountHeaders.size == values.size) {
            accountHeaderMap = accountHeaders.zip(values).toMap()
        }

    }

    override fun validate(): List<String> {
        val errors: MutableList<String> = mutableListOf()

        if (!isNumeric(accountHeaderMap.getValue(accountHeaders[0]))) {
            errors.add("Account: ${accountHeaders[0]}: ${accountHeaderMap[accountHeaders[0]]} is not Numeric")
        }
        if (!StringUtils.isAsciiPrintable(accountHeaderMap[accountHeaders[1]])) {
            errors.add("Account: ${accountHeaders[1]}: ${accountHeaderMap[accountHeaders[1]]} is not Alphanumeric")
        }
        if (!StringUtils.isAsciiPrintable(accountHeaderMap[accountHeaders[2]])) {
            errors.add("Account: ${accountHeaders[2]}: ${accountHeaderMap[accountHeaders[2]]} is not Alphanumeric")
        }
        if (!isNumeric(accountHeaderMap.getValue(accountHeaders[3]))) {
            errors.add("Account: ${accountHeaders[3]}: ${accountHeaderMap[accountHeaders[3]]} is not Numeric")
        }
        if (!isNumeric(accountHeaderMap.getValue(accountHeaders[4]))) {
            errors.add("Account: ${accountHeaders[4]}: ${accountHeaderMap[accountHeaders[4]]} is not Numeric")
        }

        if (!isNumeric(accountHeaderMap.getValue(accountHeaders[5]))) {
            errors.add("Account: ${accountHeaders[5]}: ${accountHeaderMap[accountHeaders[5]]} is not Numeric")
        }
        if (!StringUtils.isAsciiPrintable(accountHeaderMap.getValue(accountHeaders[6]))) {
            errors.add("Account: ${accountHeaders[6]}: ${accountHeaderMap[accountHeaders[6]]} is not Alphanumeric")
        }

        return errors
    }

    override fun toString(): String {
        return if (nextRecord.isEmpty()) nextRecord else StringUtils.appendIfMissing(nextRecord, "\n")
    }


}

data class BaiAccountTrailer(var nextRecord: String) : Trailer() {

    override fun get(fieldName: String) = accountTrailerMap.getValue(fieldName)
    private var accountTrailerMap: Map<String, String> = mutableMapOf()

    init {
        val values: Array<String> = StringUtils.splitByWholeSeparatorPreserveAllTokens(StringUtils.removeEnd(nextRecord, recordDelim).trim(), fieldDelim)

        if (accountTrailerFields.size == values.size) {
            accountTrailerMap = accountTrailerFields.zip(values).toMap()
        }// todo: lookup for continuation of the record

    }

    override fun toString(): String {
        return if (nextRecord.isEmpty()) nextRecord else StringUtils.appendIfMissing(nextRecord, "\n")
    }

    override fun validate(): List<String> {
        val errors: MutableList<String> = mutableListOf()
        if (!isNumeric(accountTrailerMap.getValue(accountTrailerFields[0]))) {
            errors.add("Account: ${accountTrailerFields[0]}: ${accountTrailerMap[accountTrailerFields[0]]} is not Numeric")
        }
        if (!isNumeric(accountTrailerMap.getValue(accountTrailerFields[1]))) {
            errors.add("Account: ${accountTrailerFields[1]}: ${accountTrailerMap[accountTrailerFields[1]]} is not Numeric")
        }

        if (!isNumeric(accountTrailerMap.getValue(accountTrailerFields[2]))) {
            errors.add("Account: ${accountTrailerFields[2]}: ${accountTrailerMap[accountTrailerFields[2]]} is not Numeric")
        }
        return errors
    }
}

class BaiTransaction : BaiModel {

    private var nextRecord: String = ""

    constructor()

    constructor(nextRecord: String) {
        this.nextRecord = nextRecord
    }

    override fun parse(): BaiModel {

        // ->  formulae to omit commas in the field text
        // ->  position of the field = target comma index
        // ->  single comma - single field -> use target comma index
        // ->  multiple commas - single field -> skip extra commas by incrementing index with diff in commas
        // ->  single comma - multiple fields -> target comma index + number of fields - 0
        //                                       target comma index + number of fields - 1
        //                                       target comma index + number of fields - n (repeat number of fields)
        // ->  multiple commas - multiple fields -> need solution

        // handling comma in transaction filed 'Detail Text'

        val fieldPosition = 6
        val count: Int = nextRecord.filter { ch -> ch == ',' }.count()
        var edited = nextRecord

        if (count > fieldPosition) {
            val commaIndex: Int = StringUtils.ordinalIndexOf(edited, ",", fieldPosition + 1)
            edited = StringBuilder(nextRecord).insert(commaIndex, "\\").toString()
        }
        val values = StringUtils.removeEnd(edited.trim(), recordDelim).trim().split("(?<!\\\\),".toRegex())
        (values as MutableList)[fieldPosition] = values[fieldPosition].replace("\\,", ",")
        // todo: implement 88 Continuation lookup
        if (transactionFields.size == values.size) {
            transactionRecordMap = transactionFields.zip(values).toMap()
            this.errors.addAll(validate() as ArrayList<String>)
        }
        return this
    }


    private var transactionRecordMap: Map<String, String> = mutableMapOf()

    internal fun validate(): List<String> {

        val errors: MutableList<String> = mutableListOf()

        if (!isNumeric(transactionRecordMap.getValue(transactionFields[0]))) {
            errors.add("Transaction: ${transactionFields[0]}:  ${transactionRecordMap[transactionFields[0]]} is not Numeric")
        }
        if (!isNumeric(transactionRecordMap.getValue(transactionFields[1]))) {
            errors.add("Transaction: ${transactionFields[1]}:  ${transactionRecordMap[transactionFields[1]]} is not Numeric")
        }
        if (!isNumeric(transactionRecordMap.getValue(transactionFields[2]))) {
            errors.add("Transaction: ${transactionFields[2]}:  ${transactionRecordMap[transactionFields[2]]} is not Numeric")
        }
        if (!StringUtils.isAlphanumeric(transactionRecordMap[transactionFields[3]])) {
            errors.add("Transaction: ${transactionFields[3]}:  ${transactionRecordMap[transactionFields[3]]} is not AlphaNumeric")
        }
        if (!StringUtils.isAlphanumeric(transactionRecordMap[transactionFields[4]])) {
            errors.add("Transaction: ${transactionFields[4]}:  ${transactionRecordMap[transactionFields[4]]} is not AlphaNumeric")
        }

        if (!StringUtils.isAlphanumeric(transactionRecordMap[transactionFields[5]])) {
            errors.add("Transaction: ${transactionFields[5]}:  ${transactionRecordMap[transactionFields[5]]} is not Alphanumeric")
        }
        if (!StringUtils.isAsciiPrintable(transactionRecordMap[transactionFields[6]])) {
            errors.add("Transaction: ${transactionFields[6]}:  ${transactionRecordMap[transactionFields[6]]} is not Alphanumeric")
        }


        return errors
    }

    override fun field(recordType: String, name: String): TextDataType {
        return if (recordType == TRANSACTION) {
            val value = transactionRecordMap.getValue(name)
            TextDataType(value)
        } else
            return TextDataType("")
    }


    override fun filter(recordType: String, condition: String): BaiModel? {

        if (recordType == TRANSACTION) {
            val options = condition.split("=")
            val fieldName: String = options[0].trim()
            val fieldValue: String = options[1].trim()
            val value = transactionRecordMap[fieldName]
            return if (fieldValue == value) {
                ConsoleUtils.log("matched transaction found")
                this
            } else BaiTransaction()
        }
        return BaiTransaction()
    }


    override fun toString(): String {
        return if (nextRecord.isEmpty()) nextRecord else StringUtils.appendIfMissing(nextRecord, "\n")
    }
}