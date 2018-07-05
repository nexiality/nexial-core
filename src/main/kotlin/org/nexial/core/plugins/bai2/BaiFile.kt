package org.nexial.core.plugins.bai2

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.StringUtils.startsWith
import org.nexial.core.plugins.bai2.BaiConstants.fieldDelim
import org.nexial.core.plugins.bai2.BaiConstants.fileHeaderMeta
import org.nexial.core.plugins.bai2.BaiConstants.fileHeaders
import org.nexial.core.plugins.bai2.BaiConstants.fileTrailerMeta
import org.nexial.core.plugins.bai2.BaiConstants.fileTrailers
import org.nexial.core.plugins.bai2.BaiConstants.groupHeaderMeta
import org.nexial.core.plugins.bai2.BaiConstants.recordDelim
import org.nexial.core.plugins.bai2.Validations.validateRecord
import org.nexial.core.variable.TextDataType
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.*
import kotlin.collections.ArrayList

class BaiFile : BaiModel {

    private var queue: Queue<String> = ArrayDeque<String>()

    constructor()

    constructor(queue: Queue<String>) {
        this.queue = queue
        parse()
    }

    override fun parse(): BaiModel {
        parseFileHeader()
        delegate()
        parseFileTrailer()
        return this
    }

    private fun parseFileHeader() {
        val firstRecord: String = this.queue.peek()
        if (startsWith(firstRecord, fileHeaderMeta.code)) {
            header = BaiFileHeader(firstRecord)
            this.errors.addAll((header as BaiFileHeader).validate())
            this.queue.poll()
        }
    }

    private fun delegate() {

        while (true) {
            val nextRecord: String = queue.peek()
            if (startsWith(nextRecord, groupHeaderMeta.code)) {
                val group = BaiGroup(queue)
                this.errors.addAll(group.errors)
                records.add(group)
            } else break
        }
    }

    private fun parseFileTrailer() {

        val nextRecord: String = queue.peek()
        if (startsWith(nextRecord, fileTrailerMeta.code)) {
            trailer = BaiFileTrailer(nextRecord)
            this.errors.addAll((trailer as BaiFileTrailer).validate())
            queue.poll()
        }
    }

    override fun filter(recordType: String, condition: String): BaiModel? {

//        todo: implement Nexial Filter
        val groupRecords: MutableList<BaiModel> = ArrayList()

        records.forEach { baiGroup ->
            val newBaiGroup: BaiModel? = baiGroup.filter(recordType, condition)
            if (newBaiGroup != null) {
                groupRecords.add(newBaiGroup)
            }
        }

        val newBaiFile = BaiFile()
        if (CollectionUtils.isNotEmpty(groupRecords)) {
            // return only next level records
            newBaiFile.records = groupRecords
            groupRecords.forEach { group -> newBaiFile.errors.addAll(group.errors) }
        }

        return newBaiFile
    }

    override fun field(recordType: String, name: String): TextDataType {
        return when (recordType) {
            fileHeaderMeta.type  -> TextDataType(header!!.get(name))
            fileTrailerMeta.type -> TextDataType(trailer!!.get(name))

            else         -> {
                val builder = StringBuilder()
                records.forEach { baiGroup -> builder.append(baiGroup.field(recordType, name).textValue).append(",") }
                TextDataType(builder.toString().removeSuffix(","))
            }
        }
    }

    companion object {
        @JvmStatic
        fun isNumeric(value: String): Boolean {
            val num = value.removePrefix("+")
            val numberFormat = NumberFormat.getInstance()
            val parsePosition = ParsePosition(0)
            numberFormat.parse(num.trim { it <= ' ' }, parsePosition)
            return num.isEmpty() || num.trim { it <= ' ' }.length == parsePosition.index
        }
    }

    override fun toString(): String {
        val groupString = StringBuilder()
        records.forEach { group -> groupString.append(group.toString()) }
        return "$header$groupString$trailer".replace("null", "")
    }
}

data class BaiFileHeader(private var nextRecord: String) : Header() {
    override fun get(fieldName: String) = fileHeaderMap.getValue(fieldName)

    private var fileHeaderMap: Map<String, String> = mutableMapOf()

    init {
        val values: Array<String> = StringUtils.splitByWholeSeparatorPreserveAllTokens(
            StringUtils.removeEnd(nextRecord, recordDelim).trim(), fieldDelim)
        if (fileHeaders.size == values.size) {
            val fields = mutableListOf<String>()
            fileHeaders.forEach { pair -> fields.add(pair.first) }
            fileHeaderMap = fields.zip(values).toMap()
        }

        // todo: lookup for continuation of the record
    }

    override fun validate(): MutableList<String> {
        return validateRecord(fileHeaderMap, fileHeaderMeta)
    }

    override fun toString(): String {
        return if (nextRecord.isEmpty()) nextRecord else StringUtils.appendIfMissing(nextRecord, "\n")
    }
}

data class BaiFileTrailer(private val nextRecord: String) : Trailer() {
    private var fileTrailerMap: Map<String, String> = mutableMapOf()

    override fun get(fieldName: String) = fileTrailerMap.getValue(fieldName)

    init {
        val values: Array<String> = StringUtils.splitByWholeSeparatorPreserveAllTokens(
            StringUtils.removeEnd(nextRecord, recordDelim).trim(), fieldDelim)
        if (fileTrailers.size == values.size) {
            val fields = mutableListOf<String>()
            fileTrailers.forEach { pair -> fields.add(pair.first) }
            fileTrailerMap = fields.zip(values).toMap()
        }

        // todo: lookup for continuation of the record
    }

    override fun validate(): MutableList<String> {
        return validateRecord(fileTrailerMap, fileTrailerMeta)
    }

    override fun toString(): String {
        return if (nextRecord.isEmpty()) nextRecord else StringUtils.appendIfMissing(nextRecord, "\n")
    }
}
