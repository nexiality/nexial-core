package org.nexial.core.plugins.bai2

import org.nexial.core.variable.TextDataType

abstract class BaiModel {

//    open var data: BaiModel? = null
    open var header: Header? = null
    open var records: MutableList<BaiModel> = ArrayList()
    open var trailer: Trailer? = null
    abstract fun parse(): BaiModel
    abstract fun filter(recordType: String, condition: String): BaiModel?
    abstract fun field(recordType: String, name: String): TextDataType
    abstract fun errors(): List<String>
//    open var errors = ArrayList<String>()

}

abstract class Header {
    abstract fun validate(): List<String>
}

abstract class Trailer {
    abstract fun validate(): List<String>
}
