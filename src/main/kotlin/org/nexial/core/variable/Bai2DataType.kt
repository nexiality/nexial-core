package org.nexial.core.variable

import org.apache.commons.collections4.CollectionUtils
import org.nexial.core.plugins.bai2.BaiFile
import org.nexial.core.plugins.bai2.BaiModel
import java.util.*

class Bai2DataType : ExpressionDataType<BaiModel> {

    constructor()
    constructor(textValue: String) : super(textValue)

    private var transformer: Bai2Transformer = Bai2Transformer()

    override fun getTransformer(): Transformer<Bai2DataType> {
        return transformer
    }

    override fun getName(): String {
        return "BAI2"
    }

    override fun snapshot(): ExpressionDataType<BaiModel> {

        val snapshot = Bai2DataType()
        snapshot.transformer = transformer
        snapshot.value = value
        snapshot.textValue = textValue

        return snapshot
    }

    override fun init() {
        val targetFile = textValue
        val queue = LinkedList<String>()
        CollectionUtils.addAll(queue, targetFile.split("\\s*\n\\s*".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        value = BaiFile(queue)

    }
}