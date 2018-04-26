package org.nexial.core.plugins.bai2

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.StringUtils.startsWith
import org.nexial.core.plugins.bai2.BaiConstants.FILE_HEADER_CODE
import org.nexial.core.plugins.bai2.BaiConstants.FILE_TRAILER_CODE
import org.nexial.core.plugins.bai2.BaiConstants.GROUP_HEADER_CODE
import org.nexial.core.plugins.bai2.BaiConstants.fileHeaderFields
import org.nexial.core.plugins.bai2.BaiConstants.fileTrailerFields
import org.nexial.core.plugins.bai2.BaiFile.Companion.isNumeric
import org.nexial.core.variable.TextDataType
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.*
import kotlin.collections.ArrayList

class BaiFile : BaiModel {

    var queue: Queue<String> = ArrayDeque<String>()

    var errorsList: MutableList<String> = ArrayList()
    override fun errors(): List<String> {

        return errorsList
    }

    constructor()
    constructor(queue: Queue<String>) {
        this.queue = queue
        parse()
    }

    /*override var errors: ArrayList<String>
        get() = super.errors
        set(value) {}*/
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
            this.errorsList.addAll((header as BaiFileHeader).validate())
            this.queue.poll()
        }

    }

    private fun delegate() {

        while (true) {
            val nextRecord: String = queue.peek()
            if (startsWith(nextRecord, GROUP_HEADER_CODE)) {
                val group = BaiGroup(queue)
                this.errorsList.addAll(group.errors())
                records.add(group)
            } else break
        }
    }

    private fun parseFileTrailer() {

        val nextRecord: String = queue.peek()
        if (startsWith(nextRecord, FILE_TRAILER_CODE)) {
            trailer = BaiFileTrailer(nextRecord)
            this.errorsList.addAll((trailer as BaiFileTrailer).validate())
            queue.poll()
        }

    }

    override fun filter(recordType: String, condition: String): BaiModel {

//        var nexialFilter: NexialFilter = NexialFilter.newInstance(condition)
        val groupRecords: MutableList<BaiModel> = ArrayList()

        records.forEach({ baiGroup ->
            val newBaiGroup: BaiModel? = baiGroup.filter(recordType, condition)
            if (newBaiGroup != null) {
                groupRecords.add(newBaiGroup)
//                errorsList = newBaiGroup.errors()
            }

        })

        val newBaiFile = BaiFile()
        if (CollectionUtils.isNotEmpty(groupRecords)) {
            // return only next level records
            newBaiFile.records = groupRecords
          groupRecords.forEach({group -> newBaiFile.errorsList.addAll(group.errors())})
        }
        return newBaiFile


    }

    override fun field(recordType: String, name: String): TextDataType {
        when (recordType) {
            "File Header" -> {
                val value = (header as BaiFileHeader).fileHeaderMap[name]
                return TextDataType(value)
            }
            "File Trailer" -> {
                val value = (trailer as BaiFileTrailer).fileTrailerMap[name]
                return TextDataType(value)
            }
            else -> {
                val builder = StringBuilder()
                var value: String
                records.forEach({ baiGroup ->
                    value = (baiGroup as BaiGroup).field(recordType, name).textValue
                    builder.append(value).append(",")
                })

                val newValue = builder.toString().removeSuffix(",")
                return TextDataType(newValue)
            }
        }

    }

    companion object {
        @JvmStatic
        fun isNumeric(value: String): Boolean {
            var num = value.removePrefix("+")
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

    var fileHeaderMap: Map<String, String> = mutableMapOf()

    init {
        val values: Array<String> = StringUtils.splitByWholeSeparatorPreserveAllTokens(StringUtils.removeEnd(nextRecord, "/").trim(), ",")
        if (fileHeaderFields.size == values.size) {
            fileHeaderMap = fileHeaderFields.zip(values).toMap()
        }// else fail

    }

    override fun validate(): List<String> {
        val errors: MutableList<String> = mutableListOf()
        if (!isNumeric(fileHeaderMap[fileHeaderFields[0]]!!)) {
            errors.add("File Header: ${fileHeaderFields[0]}: ${fileHeaderMap[fileHeaderFields[0]]} is not Numeric")
        }
        if (!StringUtils.isAlphanumericSpace(fileHeaderMap[fileHeaderFields[1]])) {
            errors.add("File Header: ${fileHeaderFields[1]}: ${fileHeaderMap[fileHeaderFields[1]]} is not Alphanumeric")
        }
        if (!StringUtils.isAlphanumericSpace(fileHeaderMap[fileHeaderFields[2]])) {
            errors.add("File Header: ${fileHeaderFields[2]}: ${fileHeaderMap[fileHeaderFields[2]]} is not Alphanumeric")
        }
        if (!isNumeric(fileHeaderMap[fileHeaderFields[3]]!!)) {
            errors.add("File Header: ${fileHeaderFields[3]}: ${fileHeaderMap[fileHeaderFields[3]]} is not Numeric")
        }
        if (!isNumeric(fileHeaderMap[fileHeaderFields[4]]!!)) {
            errors.add("File Header: ${fileHeaderFields[4]}: ${fileHeaderMap[fileHeaderFields[4]]} is not Numeric")
        }

        if (!isNumeric(fileHeaderMap[fileHeaderFields[5]]!!)) {
            errors.add("File Header: ${fileHeaderFields[5]}: ${fileHeaderMap[fileHeaderFields[5]]} is not Numeric")
        }
        if (!isNumeric(fileHeaderMap[fileHeaderFields[6]]!!)) {
            errors.add("File Header: ${fileHeaderFields[6]}: ${fileHeaderMap[fileHeaderFields[6]]} is not Numeric")
        }
        if (!isNumeric(fileHeaderMap[fileHeaderFields[7]]!!)) {
            errors.add("File Header: ${fileHeaderFields[7]}: ${fileHeaderMap[fileHeaderFields[7]]} is not Numeric")
        }
        if (!isNumeric(fileHeaderMap[fileHeaderFields[8]]!!)) {
            errors.add("File Header: ${fileHeaderFields[8]}: ${fileHeaderMap[fileHeaderFields[8]]} is not Numeric")
        }

        return errors
    }

    override fun toString(): String {
        return StringUtils.appendIfMissing(nextRecord, "\n")
    }
}

data class BaiFileTrailer(private val nextRecord: String) : Trailer() {
    var fileTrailerMap: Map<String, String> = mutableMapOf()

    init {
        val values: Array<String> = StringUtils.splitByWholeSeparatorPreserveAllTokens(StringUtils.removeEnd(nextRecord, "/").trim(), ",")
        if (fileTrailerFields.size == values.size) {
            fileTrailerMap = fileTrailerFields.zip(values).toMap()
        }// else fail

    }


    override fun validate(): List<String> {

        val errors: MutableList<String> = mutableListOf()
        if (!isNumeric(fileTrailerMap[fileTrailerFields[0]]!!)) {
            errors.add("File Trailer: ${fileTrailerFields[0]}: ${fileTrailerMap[fileTrailerFields[0]]} is not Numeric")

        }
        if (!StringUtils.isAlphanumericSpace(fileTrailerMap[fileTrailerFields[1]])) {
            errors.add("File Trailer: ${fileTrailerFields[1]}: ${fileTrailerMap[fileTrailerFields[1]]} is not Numeric")
        }
        if (!StringUtils.isAlphanumericSpace(fileTrailerMap[fileTrailerFields[2]])) {
            errors.add("File Trailer: ${fileTrailerFields[2]}: ${fileTrailerMap[fileTrailerFields[2]]} is not Numeric")
        }
        if (!isNumeric(fileTrailerMap[fileTrailerFields[3]]!!)) {
            errors.add("File Trailer: ${fileTrailerFields[3]}: ${fileTrailerMap[fileTrailerFields[3]]} is not Numeric")
        }
        return errors
    }

    override fun toString(): String {
        return StringUtils.appendIfMissing(nextRecord, "\n")
    }

}
