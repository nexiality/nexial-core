package org.nexial.core.plugins.bai2

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang.mutable.Mutable
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.StringUtils.startsWith
import org.nexial.core.plugins.bai2.BaiConstants.ACCOUNT_IDENTIFIER_CODE
import org.nexial.core.plugins.bai2.BaiConstants.GROUP_HEADER_CODE
import org.nexial.core.plugins.bai2.BaiConstants.GROUP_TRAILER_CODE
import org.nexial.core.plugins.bai2.BaiConstants.groupHeaderFields
import org.nexial.core.plugins.bai2.BaiConstants.groupTrailerFields
import org.nexial.core.plugins.bai2.BaiFile.Companion.isNumeric
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.variable.TextDataType
import java.util.*
import kotlin.collections.ArrayList

class BaiGroup : BaiModel {

    override fun errors(): MutableList<String> {
        return this.errorsList
    }

    var errorsList: MutableList<String> = ArrayList()
    override var header: Header? = null
    override var records: MutableList<BaiModel> = ArrayList()
    override var trailer: Trailer? = null

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
            errorsList.addAll((header as BaiGroupHeader).validate())
            this.queue.poll()
        }
    }

    private fun delegate() {

        while (true) {
            val nextRecord: String = queue.peek()
            if (startsWith(nextRecord, ACCOUNT_IDENTIFIER_CODE)) {
                val account = BaiAccount(queue)
                errorsList.addAll(account.errors())
                records.add(account)
            } else break
        }
    }

    private fun parseGroupTrailer() {
        val nextRecord = queue.peek()
        if (StringUtils.startsWith(nextRecord, GROUP_TRAILER_CODE)) {
            this.trailer = BaiGroupTrailer(nextRecord)
            errorsList.addAll((this.trailer as BaiGroupTrailer).validate())
            this.queue.poll()
        }
    }


    override fun filter(recordType: String, condition: String): BaiModel? {

        if (recordType == "Group") {
            val options = condition.split("=")
            val fieldName: String = options[0]
            val fieldValue: String = options[1]
            val value = (header as BaiGroupHeader).groupHeaderMap[fieldName]
            return if (fieldValue == value) {
                ConsoleUtils.log("matched group found")
                this
            } else null
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
//                matchedBaiGroup.header = header
                baiAccount.records = matchedAccounts
                matchedAccounts.forEach({account -> baiAccount.errorsList.addAll(account.errors())})
//                matchedBaiGroup.trailer = trailer
            }

            return baiAccount
        }
    }

    override fun field(recordType: String, name: String): TextDataType {
        when (recordType) {
            "Group Header" -> {
                val value = (header as BaiGroupHeader).groupHeaderMap[name]
                return TextDataType(value)
            }
            "Group Trailer" -> {
                val value = (trailer as BaiGroupTrailer).groupTrailerMap[name]
                return TextDataType(value)
            }
            else -> {
                val builder = StringBuilder()
                var value: String
                records.forEach({ baiAccount ->
                    value = (baiAccount as BaiAccount).field(recordType, name).textValue
                    builder.append(value).append(",")
                })
                val newValue = builder.toString().removeSuffix(",")
                return TextDataType(newValue)
            }
        }
    }

    override fun toString(): String {
        var accountsString = StringBuilder()
        records.forEach({ account ->
            if (account != null) {
                accountsString.append(account.toString())
            }

        })
        return "$header$accountsString$trailer".replace("null", "")
    }
}

data class BaiGroupHeader(private val nextRecord: String) : Header() {

    var groupHeaderMap: Map<String, String> = mutableMapOf()

    init {
        val values: Array<String> = StringUtils.splitByWholeSeparatorPreserveAllTokens(StringUtils.removeEnd(nextRecord, "/").trim(), ",")
        if (groupHeaderFields.size == values.size) {
            groupHeaderMap = groupHeaderFields.zip(values).toMap()
            println(groupHeaderMap)
        }// else fail

    }


    override fun toString(): String {
        return StringUtils.appendIfMissing(nextRecord, "\n")
    }

    override fun validate(): List<String> {
        val errors: MutableList<String> = mutableListOf()
        if (!isNumeric(groupHeaderMap[groupHeaderFields[0]]!!)) {
            errors.add(StringUtils.appendIfMissing("Group Header: ${groupHeaderFields[0]}: ${groupHeaderMap[groupHeaderFields[0]]} is not Numeric", "\n"))
        }
        if (!StringUtils.isAsciiPrintable(groupHeaderMap[groupHeaderFields[1]])) {
            errors.add("Group Header: ${groupHeaderFields[1]}: ${groupHeaderMap[groupHeaderFields[1]]} is not Alphanumeric")
        }
        if (!StringUtils.isAsciiPrintable(groupHeaderMap[groupHeaderFields[2]])) {
            errors.add("Group Header: ${groupHeaderFields[2]}: ${groupHeaderMap[groupHeaderFields[2]]} is not Alphanumeric")
        }
        if (!isNumeric(groupHeaderMap[groupHeaderFields[3]]!!)) {
            errors.add("Group Header: ${groupHeaderFields[3]}: ${groupHeaderMap[groupHeaderFields[3]]} is not Numeric")
        }
        if (!isNumeric(groupHeaderMap[groupHeaderFields[4]]!!)) {
            errors.add("Group Header: ${groupHeaderFields[4]}: ${groupHeaderMap[groupHeaderFields[4]]} is not Numeric")
        }

        if (!isNumeric(groupHeaderMap[groupHeaderFields[5]]!!)) {
            errors.add("Group Header: ${groupHeaderFields[5]}: ${groupHeaderMap[groupHeaderFields[5]]} is not Numeric")
        }
        if (!StringUtils.isAsciiPrintable(groupHeaderMap[groupHeaderFields[6]]!!)) {
            errors.add("Group Header: ${groupHeaderFields[6]}: ${groupHeaderMap[groupHeaderFields[6]]} is not Alphanumeric")
        }

//        todo: check for validity of this field?
        /*if (!StringUtils.isAlphanumericSpace(groupHeaderMap[groupHeaderFields[7]]!!)) {
            errors.add("${groupHeaderMap[groupHeaderFields[7]]} is not Balnk")
        }*/

        return errors
    }


}

data class BaiGroupTrailer(var nextRecord: String) : Trailer() {
    var groupTrailerMap: Map<String, String> = mutableMapOf()

    init {
        val values: Array<String> = StringUtils.splitByWholeSeparatorPreserveAllTokens(StringUtils.removeEnd(nextRecord, "/").trim(), ",")
        if (groupTrailerFields.size == values.size) {
            groupTrailerMap = groupTrailerFields.zip(values).toMap()
        }// else fail

    }


    override fun toString(): String {
        return StringUtils.appendIfMissing(nextRecord, "\n")
    }

    override fun validate(): List<String> {
        val errors: MutableList<String> = mutableListOf()
        if (!isNumeric(groupTrailerMap[groupTrailerFields[0]]!!)) {
            errors.add("Group Trailer: ${groupTrailerFields[0]}: ${groupTrailerMap[groupTrailerFields[0]]} is not Numeric")
        }
        if (!StringUtils.isAsciiPrintable(groupTrailerMap[groupTrailerFields[1]])) {
            errors.add("Group Trailer: ${groupTrailerFields[1]}: ${groupTrailerMap[groupTrailerFields[1]]} is not Numeric")
        }
        if (!StringUtils.isAsciiPrintable(groupTrailerMap[groupTrailerFields[2]])) {
            errors.add("Group Trailer: ${groupTrailerFields[2]}: ${groupTrailerMap[groupTrailerFields[2]]} is not Numeric")
        }
        if (!isNumeric(groupTrailerMap[groupTrailerFields[3]]!!)) {
            errors.add("Group Trailer: ${groupTrailerFields[3]}: ${groupTrailerMap[groupTrailerFields[3]]} is not Numeric")
        }

        return errors
    }
}