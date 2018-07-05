package org.nexial.core.plugins.bai2

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.StringUtils.startsWith
import org.nexial.core.plugins.bai2.BaiConstants.ACCOUNT
import org.nexial.core.plugins.bai2.BaiConstants.accountHeaderMeta
import org.nexial.core.plugins.bai2.BaiConstants.accountHeaders
import org.nexial.core.plugins.bai2.BaiConstants.accountTrailerMeta
import org.nexial.core.plugins.bai2.BaiConstants.accountTrailers
import org.nexial.core.plugins.bai2.BaiConstants.fieldDelim
import org.nexial.core.plugins.bai2.BaiConstants.recordDelim
import org.nexial.core.plugins.bai2.BaiConstants.transactionFields
import org.nexial.core.plugins.bai2.BaiConstants.transactionMeta
import org.nexial.core.plugins.bai2.Validations.validateRecord
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.variable.TextDataType
import java.util.*
import kotlin.collections.ArrayList

class BaiAccount : BaiModel {

    override fun field(recordType: String, name: String): TextDataType {
        return when (recordType) {
            accountHeaderMeta.type  -> TextDataType(header!!.get(name))
            accountTrailerMeta.type -> TextDataType(trailer!!.get(name))

            else                    -> {
                val builder = StringBuilder()
                records.forEach { transaction ->
                    val value = transaction.field(recordType, name).textValue
                    builder.append(value).append(",")
                }
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
        if (startsWith(nextRecord, accountHeaderMeta.code)) {
            this.header = BaiAccountIdentifier(nextRecord)
            errors.addAll((this.header as BaiAccountIdentifier).validate())
            queue.poll()
        }
    }

    private fun delegate() {
        while (true) {
            val nextRecord = queue.peek()
            if (startsWith(nextRecord, transactionMeta.code)) {
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
        if (startsWith(nextRecord, accountTrailerMeta.code)) {
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
        } else {
            val matchedTransactions: MutableList<BaiModel> = ArrayList()
            val baiAccount = BaiAccount()
            records.forEach { transaction ->
                val newTransaction = transaction.filter(recordType, condition)
                if (newTransaction != null) {
                    matchedTransactions.add(newTransaction)
                }
            }

            if (CollectionUtils.isNotEmpty(matchedTransactions)) {
                baiAccount.records = matchedTransactions
                return baiAccount
            }

            return baiAccount
        }
    }

    override fun toString(): String {
        val transactionsString = StringBuilder()
        records.forEach { transaction -> transactionsString.append(transaction.toString()) }
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

        val values: Array<String> = StringUtils.splitByWholeSeparatorPreserveAllTokens(
            StringUtils.removeEnd(nextRecord, recordDelim).trim(), fieldDelim)

        // todo: implement 88 Continuation lookup
        if (accountHeaders.size == values.size) {
            val fields = mutableListOf<String>()
            accountHeaders.forEach { pair -> fields.add(pair.first) }
            accountHeaderMap = fields.zip(values).toMap()
        }
    }

    override fun validate(): MutableList<String> {
        return validateRecord(accountHeaderMap, accountHeaderMeta)
    }

    override fun toString(): String {
        return if (nextRecord.isEmpty()) nextRecord else StringUtils.appendIfMissing(nextRecord, "\n")
    }
}

data class BaiAccountTrailer(var nextRecord: String) : Trailer() {

    override fun get(fieldName: String) = accountTrailerMap.getValue(fieldName)
    private var accountTrailerMap: Map<String, String> = mutableMapOf()

    init {
        val values: Array<String> = StringUtils.splitByWholeSeparatorPreserveAllTokens(
            StringUtils.removeEnd(nextRecord, recordDelim).trim(), fieldDelim)

        if (accountTrailers.size == values.size) {
            val fields = mutableListOf<String>()
            accountTrailers.forEach { pair -> fields.add(pair.first) }
            accountTrailerMap = fields.zip(values).toMap()
        }

        // todo: lookup for continuation of the record
    }

    override fun toString(): String {
        return if (nextRecord.isEmpty()) nextRecord else StringUtils.appendIfMissing(nextRecord, "\n")
    }

    override fun validate(): MutableList<String> {
        return validateRecord(accountTrailerMap, accountTrailerMeta)
    }
}

class BaiTransaction : BaiModel {

    private var nextRecord: String = ""

    constructor()

    constructor(nextRecord: String) { this.nextRecord = nextRecord }

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
            val fields = mutableListOf<String>()
            transactionFields.forEach { pair -> fields.add(pair.first) }
            transactionRecordMap = fields.zip(values).toMap()
            this.errors.addAll(validate() as ArrayList<String>)
        }
        return this
    }

    private var transactionRecordMap: Map<String, String> = mutableMapOf()

    fun validate(): MutableList<String> {
        return validateRecord(transactionRecordMap, transactionMeta)
    }

    override fun field(recordType: String, name: String): TextDataType {
        return TextDataType(if (recordType == transactionMeta.type) { transactionRecordMap.getValue(name) } else { "" })
    }

    override fun filter(recordType: String, condition: String): BaiModel? {
        if (recordType == transactionMeta.type) {
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