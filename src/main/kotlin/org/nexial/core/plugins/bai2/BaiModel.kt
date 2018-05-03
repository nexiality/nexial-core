package org.nexial.core.plugins.bai2

import org.nexial.core.variable.TextDataType

abstract class BaiModel {

    open var header: Header? = null
    open var records: MutableList<BaiModel> = ArrayList()
    open var trailer: Trailer? = null

    abstract fun parse(): BaiModel
    abstract fun filter(recordType: String, condition: String): BaiModel?
    abstract fun field(recordType: String, name: String): TextDataType

    open var errors = ArrayList<String>()

}

abstract class Header {
    abstract fun validate(): List<String>
    abstract fun get(fieldName: String): String
}

abstract class Trailer {
    abstract fun validate(): List<String>
    abstract fun get(fieldName: String): String
}
