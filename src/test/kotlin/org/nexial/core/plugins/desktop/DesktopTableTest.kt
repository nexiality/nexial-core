package org.nexial.core.plugins.desktop

import org.junit.Test
import kotlin.test.assertEquals

class DesktopTableTest {
    @Test
    fun reformatCellData() {
        val subject = DesktopTable()
        subject.extra = mapOf("Date.format" to "[M/d/yyyy h:m:s a],[MM/dd/yyyy]",
                              "Amount.format" to "[###,###.##],[0.00]",
                              "EffectiveDate.format" to "[M/d/yyyy hh:mm:ss a],[yyyy/M/d]",
                              "EffectiveDate2.format" to "[M/d/yyyy],[yyyy/M/d]")

        assertEquals("05/08/2021", subject.reformatCellData("Date", "05/8/2021 2:19:45 PM", true))
        assertEquals("05/18/2021", subject.reformatCellData("Date", "5/18/2021 0:19:45 AM", true))

        assertEquals("1234.56", subject.reformatCellData("Amount", "1,234.56", true))
        assertEquals("234.56", subject.reformatCellData("Amount", "234.56", true))
        assertEquals("234.60", subject.reformatCellData("Amount", "234.6", true))
        assertEquals("0.60", subject.reformatCellData("Amount", ".6", true))

        assertEquals("0.6", subject.reformatCellData("Amount", "0.60", false))
        assertEquals("101", subject.reformatCellData("Amount", "101.0", false))
        assertEquals("1,010.01", subject.reformatCellData("Amount", "1010.01", false))
        assertEquals("1,010", subject.reformatCellData("Amount", "1010.", false))

        assertEquals("2021/5/18", subject.reformatCellData("EffectiveDate", "5/18/2021 0:1:5 AM", true))
        assertEquals("4/9/2021 12:00:00 AM", subject.reformatCellData("EffectiveDate", "2021/4/9", false))

        assertEquals("2021/5/18", subject.reformatCellData("EffectiveDate2", "5/18/2021 0:1:5 AM", true))
        assertEquals("4/9/2021", subject.reformatCellData("EffectiveDate2", "2021/4/9", false))
    }
}