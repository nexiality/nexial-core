package org.nexial.core.plugins.bai2

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.StringUtils.splitByWholeSeparatorPreserveAllTokens
import org.apache.commons.lang3.StringUtils.startsWith
import org.nexial.core.plugins.bai2.BaiConstants.GROUP
import org.nexial.core.plugins.bai2.BaiConstants.accountHeaderMeta
import org.nexial.core.plugins.bai2.BaiConstants.fieldDelim
import org.nexial.core.plugins.bai2.BaiConstants.groupHeaderMeta
import org.nexial.core.plugins.bai2.BaiConstants.groupHeaders
import org.nexial.core.plugins.bai2.BaiConstants.groupTrailerMeta
import org.nexial.core.plugins.bai2.BaiConstants.groupTrailers
import org.nexial.core.plugins.bai2.BaiConstants.recordDelim
import org.nexial.core.plugins.bai2.Validations.validateRecord
import org.nexial.core.utils.ConsoleUtils
import org.nexial.core.variable.TextDataType
import java.util.*
import kotlin.collections.ArrayList

class BaiGroup(private var queue: Queue<String>) : BaiModel() {

    init {
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
        if (startsWith(nextRecord, groupHeaderMeta.code)) {
            this.header = BaiGroupHeader(nextRecord)
            errors.addAll((header as BaiGroupHeader).validate())
            this.queue.poll()
        }
    }

    private fun delegate() {
        while (true) {
            val nextRecord: String = queue.peek()
            if (startsWith(nextRecord, accountHeaderMeta.code)) {
                val account = BaiAccount(queue)
                errors.addAll(account.errors)
                records.add(account)
            } else break
        }
    }

    private fun parseGroupTrailer() {
        val nextRecord = queue.peek()
        if (StringUtils.startsWith(nextRecord, groupTrailerMeta.code)) {
            this.trailer = BaiGroupTrailer(nextRecord)
            errors.addAll((this.trailer as BaiGroupTrailer).validate())
            this.queue.poll()
        }
    }

    override fun filter(recordType: String, condition: String): BaiModel? {
        if (recordType == GROUP) {
            val options = condition.split("=")
            val fieldName: String = options[0]
            val fieldValue: String = options[1]
            val value = (header as BaiGroupHeader).groupHeadersMap[fieldName]
            return if (fieldValue == value) {
                ConsoleUtils.log("matched group found")
                this
            } else null
        } else {
            val baiAccount = BaiAccount()
            val matchedAccounts: MutableList<BaiModel> = ArrayList()

            records.forEach { account ->
                val newBaiAccount: BaiModel? = account.filter(recordType, condition)
                if (newBaiAccount != null) {
                    matchedAccounts.add(newBaiAccount)
                }
            }
            if (CollectionUtils.isNotEmpty(matchedAccounts)) {
                baiAccount.records = matchedAccounts
            }
            return baiAccount
        }
    }

    override fun field(recordType: String, name: String): TextDataType {
        return when (recordType) {
            groupHeaderMeta.type  -> TextDataType(header!!.get(name))
            groupTrailerMeta.type -> TextDataType(trailer!!.get(name))
            else          -> {
                val builder = StringBuilder()
                records.forEach { baiAccount ->
                    builder.append(baiAccount.field(recordType, name).textValue).append(",")
                }
                TextDataType(builder.toString().removeSuffix(","))
            }
        }
    }

    override fun toString(): String {
        val accountsString = StringBuilder()
        records.forEach { account -> accountsString.append(account.toString()) }
        return "$header$accountsString$trailer".replace("null", "")
    }
}

data class BaiGroupHeader(private val nextRecord: String) : Header() {
    override fun get(fieldName: String) = groupHeadersMap.getValue(fieldName)

    var groupHeadersMap: Map<String, String> = mutableMapOf()

    init {
        val values: Array<String> = splitByWholeSeparatorPreserveAllTokens(
            StringUtils.removeEnd(nextRecord, recordDelim).trim(), fieldDelim)
        if (groupHeaders.size == values.size) {
            val fields = mutableListOf<String>()
            groupHeaders.forEach { pair -> fields.add(pair.first) }
            groupHeadersMap = fields.zip(values).toMap()
        }

        // todo: lookup for continuation of the record
    }

    override fun toString(): String {
        return if (nextRecord.isEmpty()) nextRecord else StringUtils.appendIfMissing(nextRecord, "\n")
    }

    override fun validate(): MutableList<String> {
        return validateRecord(groupHeadersMap, groupHeaderMeta)
    }
}

data class BaiGroupTrailer(private var nextRecord: String) : Trailer() {

    override fun get(fieldName: String) = groupTrailerMap.getValue(fieldName)

    private var groupTrailerMap: Map<String, String> = mutableMapOf()

    init {
        val values: Array<String> = splitByWholeSeparatorPreserveAllTokens(
            StringUtils.removeEnd(nextRecord, recordDelim).trim(), fieldDelim)

        if (groupTrailers.size == values.size) {
            val fields = mutableListOf<String>()
            groupTrailers.forEach { pair -> fields.add(pair.first) }
            groupTrailerMap = fields.zip(values).toMap()
        }

        // todo: lookup for continuation of the record
    }

    override fun toString(): String {
        return if (nextRecord.isEmpty()) nextRecord else StringUtils.appendIfMissing(nextRecord, "\n")
    }

    override fun validate(): MutableList<String> {
        return validateRecord(groupTrailerMap, groupTrailerMeta)
    }
}
