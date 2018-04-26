package org.nexial.core.variable

import org.apache.commons.lang3.StringUtils
import org.nexial.core.ExecutionThread
import org.nexial.core.NexialConst.Data.TEXT_DELIM
import org.nexial.core.plugins.bai2.BaiError
import org.nexial.core.plugins.bai2.BaiModel
import java.lang.reflect.Method


class Bai2Transformer : Transformer<Bai2DataType>() {

    private val functionToParam: MutableMap<String, Int> = discoverFunctions(Bai2Transformer::class.java)

    private val functions = toFunctionMap(functionToParam, Bai2Transformer::class.java, Bai2DataType::class.java)

    override fun listSupportedFunctions(): MutableMap<String, Int> {
        return functionToParam
    }

    override fun listSupportedMethods(): MutableMap<String, Method> {
        return functions
    }

    fun store(data: Bai2DataType, Var: String): Bai2DataType {
        snapshot(Var, data)
        return data
    }

    fun save(data: Bai2DataType, path: String): ExpressionDataType<Any> {
        return super.save(data, path)
    }

    fun errors(data: Bai2DataType): ListDataType{

        val errors: List<String>? = data.getValue().errors()
        return ListDataType(errors.toString())
    }

    fun field(data: Bai2DataType, recordType: String, name: String): TextDataType {

        return data.getValue().field(recordType, name)
    }

    fun csv(data: Bai2DataType): CsvDataType {
        val context = ExecutionThread.get()
        val text = StringUtils.replace(data.getValue().toString(), "\r\n", "\n")
        val csvDataType = CsvDataType(text)
        csvDataType.isHeader = false
        csvDataType.delim = context.getStringData(TEXT_DELIM)
        csvDataType.recordDelim = "\n"
        csvDataType.setReadyToParse(true)
        csvDataType.parse()
        return csvDataType

    }

    fun count(data: Bai2DataType): NumberDataType {
        val count = NumberDataType("0")
        count.setValue(data.getValue().records.size)
        count.setTextValue(data.getValue().records.size.toString())
        return count
    }

    fun filter(data: Bai2DataType, recordType: String, condition: String): Bai2DataType {
        if (data.getValue() == null || StringUtils.isAnyBlank(recordType, condition)) {
            return data
        }

//        var clazz = Class.forName("org.nexial.core.plugins.bai2.BaiGroup")
//        val baiFileInstance = (clazz.newInstance() as BaiModel)
//        println("type ${baiFileInstance.javaClass.name}")
//        baiFileInstance.data = data.getValue()


        val baiFileInstance = data.getValue()
        val newBiaData: BaiModel = baiFileInstance.filter(recordType, condition)!!
        data.setTextValue(newBiaData.toString())
        data.setValue(newBiaData)

        return data
    }

}