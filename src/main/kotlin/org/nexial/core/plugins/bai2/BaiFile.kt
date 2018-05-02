package org.nexial.core.plugins.bai2

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.StringUtils.startsWith
import org.nexial.core.plugins.bai2.BaiConstants.FILE_HEADER
import org.nexial.core.plugins.bai2.BaiConstants.FILE_HEADER_CODE
import org.nexial.core.plugins.bai2.BaiConstants.FILE_TRAILER
import org.nexial.core.plugins.bai2.BaiConstants.FILE_TRAILER_CODE
import org.nexial.core.plugins.bai2.BaiConstants.GROUP_HEADER_CODE
import org.nexial.core.plugins.bai2.BaiConstants.fieldDelim
import org.nexial.core.plugins.bai2.BaiConstants.fileHeaders
import org.nexial.core.plugins.bai2.BaiConstants.fileTrailerFields
import org.nexial.core.plugins.bai2.BaiConstants.recordDelim
import org.nexial.core.plugins.bai2.BaiFile.Companion.isNumeric
import org.nexial.core.variable.TextDataType
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.*
import kotlin.collections.ArrayList

class BaiFile : BaiModel {

    var queue: Queue<String> = ArrayDeque<String>()

    constructor()

    constructor(queue: Queue<String>) {
        this.queue = queue
        parse()
    }

    override var errors: ArrayList<String>
        get() = super.errors
        set(value) {}

    override var header: Header? = null
    override var records: MutableList<BaiModel> = ArrayList()
    override var trailer: Trailer? = null

    override fun parse(): BaiModel {
        parseFileHeader()
        delegate()
        parseFileTrailer()
        return this
    }

    private fun parseFileHeader() {
        val firstRecord: String = this.queue.peek()
        if (startsWith(firstRecord, FILE_HEADER_CODE)) {
            header = BaiFileHeader(firstRecord)
            this.errors.addAll((header as BaiFileHeader).validate())
            this.queue.poll()
        }

    }

    private fun delegate() {

        while (true) {
            val nextRecord: String = queue.peek()
            if (startsWith(nextRecord, GROUP_HEADER_CODE)) {
                val group = BaiGroup(queue)
                this.errors.addAll(group.errors)
                records.add(group)
            } else break
        }
    }

    private fun parseFileTrailer() {

        val nextRecord: String = queue.peek()
        if (startsWith(nextRecord, FILE_TRAILER_CODE)) {
            trailer = BaiFileTrailer(nextRecord)
            this.errors.addAll((trailer as BaiFileTrailer).validate())
            queue.poll()
        }

    }

    override fun filter(recordType: String, condition: String): BaiModel {

//        todo: implement Nexial Filter
        val groupRecords: MutableList<BaiModel> = ArrayList()

        records.forEach({ baiGroup ->
            val newBaiGroup: BaiModel? = baiGroup.filter(recordType, condition)
            if (newBaiGroup != null) {
                groupRecords.add(newBaiGroup)
            }

        })

        val newBaiFile = BaiFile()
        if (CollectionUtils.isNotEmpty(groupRecords)) {
            // return only next level records
            newBaiFile.records = groupRecords
            groupRecords.forEach({ group -> newBaiFile.errors.addAll(group.errors) })
        }
        return newBaiFile


    }

    override fun field(recordType: String, name: String): TextDataType {
        return when (recordType) {

            FILE_HEADER -> TextDataType(header!!.get(fieldName = name))

            FILE_TRAILER -> TextDataType(trailer!!.get(name))

            else -> {
                val builder = StringBuilder()
                var value: String
                records.forEach({ baiGroup ->
                    value = (baiGroup as BaiGroup).field(recordType, name).textValue
                    builder.append(value).append(",")
                })

                val newValue = builder.toString().removeSuffix(",")
                TextDataType(newValue)
            }
        }

    }

    companion object {
        @JvmStatic
        fun isNumeric(value: String): Boolean {
            val num = value.removePrefix("+")
            val numberFormat = NumberFormat.getInstance()
            val parsePosition = ParsePosition(0)
            numberFormat.parse(num.trim({ it <= ' ' }), parsePosition)
            return num.isEmpty() || num.trim({ it <= ' ' }).length == parsePosition.index
        }
    }

    override fun toString(): String {
        val groupString = StringBuilder()
        records.forEach({ group ->
            groupString.append(group.toString())
        })
        return "$header$groupString$trailer".replace("null", "")
    }
}

data class BaiFileHeader(private var nextRecord: String) : Header() {
    override fun get(fieldName: String) = fileHeaderMap.getValue(fieldName)

    var fileHeaderMap: Map<String, String> = mutableMapOf()

    init {
        val values: Array<String> = StringUtils.splitByWholeSeparatorPreserveAllTokens(
                StringUtils.removeEnd(nextRecord, recordDelim).trim(), fieldDelim)
        if (fileHeaders.size == values.size) {
            fileHeaderMap = fileHeaders.zip(values).toMap()
        }// else fail

    }

    override fun validate(): List<String> {
        val errors: MutableList<String> = mutableListOf()
        if (!isNumeric(fileHeaderMap.getValue(fileHeaders[0]))) {
            errors.add("File Header: ${fileHeaders[0]}: ${fileHeaderMap[fileHeaders[0]]} is not Numeric")
        }
        if (!StringUtils.isAlphanumeric(fileHeaderMap[fileHeaders[1]])) {
            errors.add("File Header: ${fileHeaders[1]}: ${fileHeaderMap[fileHeaders[1]]} is not Alphanumeric")
        }
        if (!StringUtils.isAlphanumeric(fileHeaderMap[fileHeaders[2]])) {
            errors.add("File Header: ${fileHeaders[2]}: ${fileHeaderMap[fileHeaders[2]]} is not Alphanumeric")
        }
        if (!isNumeric(fileHeaderMap.getValue(fileHeaders[3]))) {
            errors.add("File Header: ${fileHeaders[3]}: ${fileHeaderMap[fileHeaders[3]]} is not Numeric")
        }
        if (!isNumeric(fileHeaderMap.getValue(fileHeaders[4]))) {
            errors.add("File Header: ${fileHeaders[4]}: ${fileHeaderMap[fileHeaders[4]]} is not Numeric")
        }
        if (!isNumeric(fileHeaderMap.getValue(fileHeaders[5]))) {
            errors.add("File Header: ${fileHeaders[5]}: ${fileHeaderMap[fileHeaders[5]]} is not Numeric")
        }
        if (!isNumeric(fileHeaderMap.getValue(fileHeaders[6]))) {
            errors.add("File Header: ${fileHeaders[6]}: ${fileHeaderMap[fileHeaders[6]]} is not Numeric")
        }
        if (!isNumeric(fileHeaderMap.getValue(fileHeaders[7]))) {
            errors.add("File Header: ${fileHeaders[7]}: ${fileHeaderMap[fileHeaders[7]]} is not Numeric")
        }
        if (!isNumeric(fileHeaderMap.getValue(fileHeaders[8]))) {
            errors.add("File Header: ${fileHeaders[8]}: ${fileHeaderMap[fileHeaders[8]]} is not Numeric")
        }

        return errors
    }

    override fun toString(): String {
        return if (nextRecord.isEmpty()) nextRecord else StringUtils.appendIfMissing(nextRecord, "\n")
    }
}

data class BaiFileTrailer(private val nextRecord: String) : Trailer() {
    var fileTrailerMap: Map<String, String> = mutableMapOf()

    override fun get(fieldName: String) = fileTrailerMap.getValue(fieldName)

    init {
        val values: Array<String> = StringUtils.splitByWholeSeparatorPreserveAllTokens(StringUtils.removeEnd(nextRecord, recordDelim).trim(), fieldDelim)
        if (fileTrailerFields.size == values.size) {
            fileTrailerMap = fileTrailerFields.zip(values).toMap()
        }// else fail

    }


    override fun validate(): List<String> {

        val errors: MutableList<String> = mutableListOf()
        if (!isNumeric(fileTrailerMap.getValue(fileTrailerFields[0]))) {
            errors.add("File Trailer: ${fileTrailerFields[0]}: ${fileTrailerMap[fileTrailerFields[0]]} is not Numeric")

        }
        if (!isNumeric(fileTrailerMap.getValue(fileTrailerFields[1]))) {
            errors.add("File Trailer: ${fileTrailerFields[1]}: ${fileTrailerMap[fileTrailerFields[1]]} is not Numeric")
        }
        if (!isNumeric(fileTrailerMap.getValue(fileTrailerFields[2]))) {
            errors.add("File Trailer: ${fileTrailerFields[2]}: ${fileTrailerMap[fileTrailerFields[2]]} is not Numeric")
        }
        if (!isNumeric(fileTrailerMap.getValue(fileTrailerFields[3]))) {
            errors.add("File Trailer: ${fileTrailerFields[3]}: ${fileTrailerMap[fileTrailerFields[3]]} is not Numeric")
        }
        return errors
    }

    override fun toString(): String {
        return if (nextRecord.isEmpty()) nextRecord else StringUtils.appendIfMissing(nextRecord, "\n")
    }

}
