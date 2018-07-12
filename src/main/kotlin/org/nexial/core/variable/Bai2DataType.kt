package org.nexial.core.variable

import org.nexial.core.plugins.bai2.BaiFile
import org.nexial.core.plugins.bai2.BaiModel
import java.util.*

class Bai2DataType : ExpressionDataType<BaiModel> {

    constructor()
    constructor(textValue: String) : super(textValue)

    private var transformer: Bai2Transformer = Bai2Transformer()

    override fun getTransformer(): Transformer<Bai2DataType> = transformer

    override fun getName(): String = "BAI2"

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
        queue.addAll(targetFile.split("\\s*\n\\s*".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
        value = BaiFile(queue)
    }
}