package org.nexial.core.plugins.bai2

import org.apache.commons.beanutils.PropertyUtils
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.StringUtils.startsWith
import org.nexial.core.plugins.bai2.BaiConstants.ACCOUNT_IDENTIFIER_CODE
import org.nexial.core.plugins.bai2.BaiConstants.accountHeaderFields
import org.nexial.core.plugins.bai2.BaiConstants.accountTrailerFields
import org.nexial.core.plugins.bai2.BaiConstants.transactionFields
import org.nexial.core.plugins.bai2.BaiFile.Companion.isNumeric
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.variable.TextDataType
import java.util.*
import kotlin.collections.ArrayList

class BaiAccount : BaiModel {
    var errorsList: MutableList<String> = ArrayList()
    override fun field(recordType: String, name: String): TextDataType {
        when (recordType) {
            "Account Header" -> {
                val value = (header as BaiAccountIdentifier).accountHeaderMap[name]
                return TextDataType(value)
            }
            "Account Trailer" -> {
                val value = (trailer as BaiAccountTrailer).accountTrailerMap[name]
                return TextDataType(value)
            }
            else -> {
                val builder = StringBuilder()
                var value: String
                records.forEach({ transaction ->
                    value = (transaction as BaiTransaction).field(recordType, name).textValue
                    builder.append(value).append(",")
                })
                val newValue = builder.toString().removeSuffix(",")
                return TextDataType(newValue)
            }
        }
    }

    override fun errors(): List<String> {
        return this.errorsList
    }

    var queue: Queue<String> = ArrayDeque<String>()
    override var header: Header? = null
    override var records: MutableList<BaiModel> = ArrayList()
    override var trailer: Trailer? = null

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
            errorsList.addAll((this.header as BaiAccountIdentifier).validate())
            queue.poll()
        }
    }

    private fun delegate() {
        while (true) {
            val nextRecord = queue.peek()
            if (StringUtils.startsWith(nextRecord, BaiConstants.TRANSACTION_CODE)) {
                val transaction = BaiTransaction(queue.peek()).parse()
                this.records.add(transaction)
                errorsList.addAll(transaction.errors())
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
            errorsList.addAll((this.trailer as BaiAccountTrailer).validate())
            queue.poll()
        }
    }

    override fun filter(recordType: String, condition: String): BaiModel? {
        if (recordType == "Account") {
            var options = condition.split("=")
            var fieldName: String = options[0].trim()
            val fieldValue: String = options[1].trim()
            val value = (header as BaiAccountIdentifier).accountHeaderMap.get(fieldName)
            return if (fieldValue == value) {
                ConsoleUtils.log("matched account found")
                this
            } else null
        } else {
            val matchedTransactions: MutableList<BaiModel> = ArrayList()
            val baiAccount = BaiAccount()
            records.forEach({ transaction ->
                val newTransaction = transaction.filter(recordType, condition)
                if (newTransaction != null) {
                    matchedTransactions.add(newTransaction)
                }
            })

            if (CollectionUtils.isNotEmpty(matchedTransactions)) {
//                baiAccount.header = header
                baiAccount.records = matchedTransactions
                matchedTransactions.forEach({transaction -> baiAccount.errorsList.addAll(transaction.errors())})
                return baiAccount
//                baiAccount.trailer = trailer
            }
            return baiAccount
        }
    }

    override fun toString(): String {
        var transactionsString = StringBuilder()
        records.forEach({ transaction ->
            if (transaction != null) {
                transactionsString.append(transaction.toString())
            }
        })
        return "$header$transactionsString$trailer".replace("null", "")
    }
}

data class BaiAccountIdentifier(private val nextRecord: String) : Header() {
    var accountHeaderMap: Map<String, String> = mutableMapOf()

    init {
        val values: Array<String> = StringUtils.splitByWholeSeparatorPreserveAllTokens(StringUtils.removeEnd(nextRecord, "/").trim(), ",")
        if (accountHeaderFields.size == values.size) {
            accountHeaderMap = accountHeaderFields.zip(values).toMap()
        }// else fail

    }

    override fun validate(): List<String> {
        val errors: MutableList<String> = mutableListOf()

        if (!isNumeric(accountHeaderMap[accountHeaderFields[0]]!!)) {
            errors.add("Account: ${accountHeaderFields[0]}: ${accountHeaderMap[accountHeaderFields[0]]} is not Numeric")
        }
        if (!StringUtils.isAsciiPrintable(accountHeaderMap[accountHeaderFields[1]])) {
            errors.add("Account: ${accountHeaderFields[1]}: ${accountHeaderMap[accountHeaderFields[1]]} is not Alphanumeric")
        }
        if (!StringUtils.isAsciiPrintable(accountHeaderMap[accountHeaderFields[2]])) {
            errors.add("Account: ${accountHeaderFields[2]}: ${accountHeaderMap[accountHeaderFields[2]]} is not Alphanumeric")
        }
        if (!isNumeric(accountHeaderMap[accountHeaderFields[3]]!!)) {
            errors.add("Account: ${accountHeaderFields[3]}: ${accountHeaderMap[accountHeaderFields[3]]} is not Numeric")
        }
        if (!isNumeric(accountHeaderMap[accountHeaderFields[4]]!!)) {
            errors.add("Account: ${accountHeaderFields[4]}: ${accountHeaderMap[accountHeaderFields[4]]} is not Numeric")
        }

        if (!isNumeric(accountHeaderMap[accountHeaderFields[5]]!!)) {
            errors.add("Account: ${accountHeaderFields[5]}: ${accountHeaderMap[accountHeaderFields[5]]} is not Numeric")
        }
        if (!StringUtils.isAsciiPrintable(accountHeaderMap[accountHeaderFields[6]]!!)) {
            errors.add("Account: ${accountHeaderFields[6]}: ${accountHeaderMap[accountHeaderFields[6]]} is not Alphanumeric")
        }

        return errors
    }

    override fun toString(): String {
        return StringUtils.appendIfMissing(nextRecord, "\n")
    }


}

data class BaiAccountTrailer(var nextRecord: String) : Trailer() {


    var accountTrailerMap: Map<String, String> = mutableMapOf()

    init {
        val values: Array<String> = StringUtils.splitByWholeSeparatorPreserveAllTokens(StringUtils.removeEnd(nextRecord, "/").trim(), ",")
        if (accountTrailerFields.size == values.size) {
            accountTrailerMap = accountTrailerFields.zip(values).toMap()
        }// else fail

    }

    override fun toString(): String {
        return StringUtils.appendIfMissing(nextRecord, "\n")
    }

    override fun validate(): List<String> {
        val errors: MutableList<String> = mutableListOf()
        if (!isNumeric(accountTrailerMap[accountTrailerFields[0]]!!)) {
            errors.add("Account: ${accountTrailerFields[0]}: ${accountTrailerMap[accountTrailerFields[0]]} is not Numeric")
        }
        if (!isNumeric(accountTrailerMap[accountTrailerFields[1]]!!)) {
            errors.add("Account: ${accountTrailerFields[1]}: ${accountTrailerMap[accountTrailerFields[1]]} is not Numeric")
        }

        if (!isNumeric(accountTrailerMap[accountTrailerFields[2]]!!)) {
            errors.add("Account: ${accountTrailerFields[2]}: ${accountTrailerMap[accountTrailerFields[2]]} is not Numeric")
        }
        return errors
    }
}

data class BaiTransaction(private val nextRecord: String) : BaiModel() {
    var errorsList: MutableList<String> = ArrayList()


    override fun parse(): BaiModel {
        val values: Array<String> = StringUtils.splitByWholeSeparatorPreserveAllTokens(StringUtils.removeEnd(nextRecord, "/").trim(), ",")
        if (transactionFields.size == values.size) {
            transactionRecordMap = transactionFields.zip(values).toMap()
            this.errorsList.addAll(validate() as ArrayList<String>)
        }// else fail

        return this
    }

    override fun errors(): List<String> {
        return this.errorsList
    }


    private var transactionRecordMap: Map<String, String> = mutableMapOf()

    internal fun validate(): List<String> {

        val errors: MutableList<String> = mutableListOf()

        if (!isNumeric(transactionRecordMap[transactionFields[0]]!!)) {
            errors.add("Transaction: ${transactionFields[0]}:  ${transactionRecordMap[transactionFields[0]]} is not Numeric")
        }
        if (!StringUtils.isAsciiPrintable(transactionRecordMap[transactionFields[1]])) {
            errors.add("Transaction: ${transactionFields[1]}:  ${transactionRecordMap[transactionFields[1]]} is not Alphanumeric")
        }
        if (!StringUtils.isAsciiPrintable(transactionRecordMap[transactionFields[2]])) {
            errors.add("Transaction: ${transactionFields[2]}:  ${transactionRecordMap[transactionFields[2]]} is not Alphanumeric")
        }
        if (!isNumeric(transactionRecordMap[transactionFields[3]]!!)) {
            errors.add("Transaction: ${transactionFields[3]}:  ${transactionRecordMap[transactionFields[3]]} is not Numeric")
        }
        if (!isNumeric(transactionRecordMap[transactionFields[4]]!!)) {
            errors.add("Transaction: ${transactionFields[4]}:  ${transactionRecordMap[transactionFields[4]]} is not Numeric")
        }

        if (!StringUtils.isAsciiPrintable(transactionRecordMap[transactionFields[5]]!!)) {
            errors.add("Transaction: ${transactionFields[5]}:  ${transactionRecordMap[transactionFields[5]]} is not Alphanumeric")
        }
        if (!StringUtils.isAsciiPrintable(transactionRecordMap[transactionFields[6]]!!)) {
            errors.add("Transaction: ${transactionFields[6]}:  ${transactionRecordMap[transactionFields[6]]} is not Alphanumeric")
        }


        return errors
    }

    override fun field(recordType: String, name: String): TextDataType {
        return if (recordType == "Transaction") {
            val value = transactionRecordMap[name]
            if (value == null) {
                println(" null value $nextRecord")
            }
            TextDataType(value)
        } else
            return TextDataType("")
    }


    override fun filter(recordType: String, condition: String): BaiModel? {

        if (recordType == "Transaction") {
            val options = condition.split("=")
            val fieldName: String = options[0].trim()
            val fieldValue: String = options[1].trim()
            val value = transactionRecordMap[fieldName]
            return if (fieldValue == value) {
                this
            } else null
        }
        return null
    }

    override fun toString(): String {
        return StringUtils.appendIfMissing(nextRecord, "\n")
    }
}