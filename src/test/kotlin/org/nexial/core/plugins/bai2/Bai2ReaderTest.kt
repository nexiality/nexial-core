package org.nexial.core.plugins.bai2

import org.junit.Test
import org.nexial.core.NexialTestUtils

class Bai2ReaderTest {

    @Test
    fun parseTest() {
        val inputFile: String = (NexialTestUtils.getResourcePath(this.javaClass, this.javaClass.simpleName + "1.txt"))
        val bai2Reader = Bai2Reader()
        val baiModel = bai2Reader.parse(inputFile)
        println("total groups " + baiModel.records[0].records.size)
        println("total transactions " + baiModel.records.size)
        println("total accounts " + baiModel.records[0].records[0].records.size)
//        baiModel.filterGroup(2,"2")
        println(baiModel.filter("Group", "GROUP_STATUS=2").toString())
    }


}