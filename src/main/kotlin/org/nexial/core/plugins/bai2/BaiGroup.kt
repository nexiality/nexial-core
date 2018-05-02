package org.nexial.core.plugins.bai2

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.StringUtils.splitByWholeSeparatorPreserveAllTokens
import org.apache.commons.lang3.StringUtils.startsWith
import org.nexial.core.plugins.bai2.BaiConstants.ACCOUNT_IDENTIFIER_CODE
import org.nexial.core.plugins.bai2.BaiConstants.GROUP
import org.nexial.core.plugins.bai2.BaiConstants.GROUP_HEADER
import org.nexial.core.plugins.bai2.BaiConstants.GROUP_HEADER_CODE
import org.nexial.core.plugins.bai2.BaiConstants.GROUP_TRAILER
import org.nexial.core.plugins.bai2.BaiConstants.GROUP_TRAILER_CODE
import org.nexial.core.plugins.bai2.BaiConstants.fieldDelim
import org.nexial.core.plugins.bai2.BaiConstants.groupHeaders
import org.nexial.core.plugins.bai2.BaiConstants.groupTrailerFields
import org.nexial.core.plugins.bai2.BaiConstants.recordDelim
import org.nexial.core.plugins.bai2.BaiFile.Companion.isNumeric
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.variable.TextDataType
import java.util.*
import kotlin.collections.ArrayList

class BaiGroup : BaiModel {

//    override var errors: ArrayList<String>
//        get() = super.errors
//        set(value) {}

//    override var header: Header? = null
//    override var records: MutableList<BaiModel> = ArrayList()
//    override var trailer: Trailer? = null

    private var queue: Queue<String> = LinkedList<String>()

    constructor()
    constructor(queue: Queue<String>) {
        this.queue = queue
        parse()
    }

    override fun parse(): BaiModel {
        parseGroupHeader()
        delegate()
        parseGroupTrailer()
        return this
    }

    private fun parseGroupHeader() {
        val nextRecord: String = this.queue.peek()
        if (startsWith(nextRecord, GROUP_HEADER_CODE)) {
            this.header = BaiGroupHeader(nextRecord)
            errors.addAll((header as BaiGroupHeader).validate())
            this.queue.poll()
        }
    }

    private fun delegate() {

        while (true) {
            val nextRecord: String = queue.peek()
            if (startsWith(nextRecord, ACCOUNT_IDENTIFIER_CODE)) {
                val account = BaiAccount(queue)
                errors.addAll(account.errors)
                records.add(account)
            } else break
        }
    }

    private fun parseGroupTrailer() {
        val nextRecord = queue.peek()
        if (StringUtils.startsWith(nextRecord, GROUP_TRAILER_CODE)) {
            this.trailer = BaiGroupTrailer(nextRecord)
            errors.addAll((this.trailer as BaiGroupTrailer).validate())
            this.queue.poll()
        }
    }


    override fun filter(recordType: String, condition: String): BaiModel {

        if (recordType == GROUP) {
            val options = condition.split("=")
            val fieldName: String = options[0]
            val fieldValue: String = options[1]
            val value = (header as BaiGroupHeader).headers[fieldName]
            return if (fieldValue == value) {
                ConsoleUtils.log("matched group found")
                this
            } else BaiGroup()
        } else {
            val baiAccount = BaiAccount()
            val matchedAccounts: MutableList<BaiModel> = ArrayList()

            records.forEach({ account ->
                val newBaiAccount: BaiModel? = account.filter(recordType, condition)
                if (newBaiAccount != null) {
                    matchedAccounts.add(newBaiAccount)
                }
            })
            if (CollectionUtils.isNotEmpty(matchedAccounts)) {
                baiAccount.records = matchedAccounts
            }
            return baiAccount
        }
    }


    override fun field(recordType: String, name: String): TextDataType {
        return when (recordType) {
            GROUP_HEADER -> TextDataType(header!!.get(name))
            GROUP_TRAILER -> TextDataType(trailer!!.get(name))
            else -> {
                val builder = StringBuilder()
                var value: String
                records.forEach({ baiAccount ->
                    value = (baiAccount as BaiAccount).field(recordType, name).textValue
                    builder.append(value).append(",")
                })
                val newValue = builder.toString().removeSuffix(",")
                TextDataType(newValue)
            }
        }
    }

    override fun toString(): String {
        val accountsString = StringBuilder()
        records.forEach({ account -> accountsString.append(account.toString()) })
        return "$header$accountsString$trailer".replace("null", "")
    }
}

data class BaiGroupHeader(private val nextRecord: String) : Header() {
    override fun get(fieldName: String) = headers.getValue(fieldName)

    var headers: Map<String, String> = mutableMapOf()

    init {
        val values: Array<String> = splitByWholeSeparatorPreserveAllTokens(StringUtils.removeEnd(nextRecord, recordDelim).trim(), fieldDelim)
        if (groupHeaders.size == values.size) {
            headers = groupHeaders.zip(values).toMap()
        }// else fail

    }

    override fun toString(): String {
        return if (nextRecord.isEmpty()) nextRecord else StringUtils.appendIfMissing(nextRecord, "\n")
    }

    override fun validate(): List<String> {
        val errors: MutableList<String> = mutableListOf()

        // todo: check min/max length, check allowable values, check date format
        if (!isNumeric(headers.getValue(groupHeaders[0]))) {
            errors.add("Group Header: ${groupHeaders[0]}: ${headers[groupHeaders[0]]} is not Numeric")
        }
        if (!StringUtils.isAlphanumeric(headers[groupHeaders[1]])) {
            errors.add("Group Header: ${groupHeaders[1]}: ${headers[groupHeaders[1]]} is not Alphanumeric")
        }
        if (!StringUtils.isAlphanumeric(headers[groupHeaders[2]])) {
            errors.add("Group Header: ${groupHeaders[2]}: ${headers[groupHeaders[2]]} is not Alphanumeric")
        }
        if (!isNumeric(headers.getValue(groupHeaders[3]))) {
            errors.add("Group Header: ${groupHeaders[3]}: ${headers[groupHeaders[3]]} is not Numeric")
        }
        if (!isNumeric(headers.getValue(groupHeaders[4]))) {
            errors.add("Group Header: ${groupHeaders[4]}: ${headers[groupHeaders[4]]} is not Numeric")
        }
        if (!isNumeric(headers.getValue(groupHeaders[5]))) {
            errors.add("Group Header: ${groupHeaders[5]}: ${headers[groupHeaders[5]]} is not Numeric")
        }
        if (!StringUtils.isAlphanumeric(headers[groupHeaders[6]]!!)) {
            errors.add("Group Header: ${groupHeaders[6]}: ${headers[groupHeaders[6]]} is not Alphanumeric")
        }
        if (!isNumeric(headers.getValue(groupHeaders[7]))) {
            errors.add("Group Header: ${groupHeaders[7]}: ${headers[groupHeaders[7]]} is not Numeric")
        }

        return errors
    }


}

data class BaiGroupTrailer(private var nextRecord: String) : Trailer() {

    override fun get(fieldName: String) = groupTrailerMap.getValue(fieldName)

    var groupTrailerMap: Map<String, String> = mutableMapOf()

    init {
        val values: Array<String> = splitByWholeSeparatorPreserveAllTokens(StringUtils.removeEnd(nextRecord, recordDelim).trim(), fieldDelim)
        if (groupTrailerFields.size == values.size) {
            groupTrailerMap = groupTrailerFields.zip(values).toMap()
        }// else fail

    }


    override fun toString(): String {
        return if (nextRecord.isEmpty()) nextRecord else StringUtils.appendIfMissing(nextRecord, "\n")
    }

    override fun validate(): List<String> {
        val errors: MutableList<String> = mutableListOf()
        if (!isNumeric(groupTrailerMap.getValue(groupTrailerFields[0]))) {
            errors.add("Group Trailer: ${groupTrailerFields[0]}: ${groupTrailerMap[groupTrailerFields[0]]} is not Numeric")
        }
        if (!isNumeric(groupTrailerMap.getValue(groupTrailerFields[1]))) {
            errors.add("Group Trailer: ${groupTrailerFields[1]}: ${groupTrailerMap[groupTrailerFields[1]]} is not Numeric")
        }
        if (!isNumeric(groupTrailerMap.getValue(groupTrailerFields[2]))) {
            errors.add("Group Trailer: ${groupTrailerFields[2]}: ${groupTrailerMap[groupTrailerFields[2]]} is not Numeric")
        }
        if (!isNumeric(groupTrailerMap.getValue(groupTrailerFields[3]))) {
            errors.add("Group Trailer: ${groupTrailerFields[3]}: ${groupTrailerMap[groupTrailerFields[3]]} is not Numeric")
        }

        return errors
    }
}