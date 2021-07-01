package org.nexial.core.tms.model


data class TMSAccessData(val source: String, val user: String,
                         val password: String, val url: String,
                         val organisation: String? = null)

data class TestcaseOrder(val id: Int, val testName: String, val sequenceNumber: Int, val suiteEntryType:String)


