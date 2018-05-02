package org.nexial.core.plugins.bai2

object BaiConstants {

    const val FILE_HEADER_CODE = "01"
    const val GROUP_HEADER_CODE = "02"
    const val ACCOUNT_IDENTIFIER_CODE = "03"
    const val TRANSACTION_CODE = "16"
    const val CONTINUATION_CODE = "88"
    const val ACCOUNT_TRAILER_CODE = "49"
    const val GROUP_TRAILER_CODE = "98"
    const val FILE_TRAILER_CODE = "99"

    val fileHeaderFields = mutableListOf("Record Code", "Sender ABA", "Receiver ABA", "File Creation Date", "File Creation Time", "File ID", "Record Length", "File Block Size", "File Version Number")
    val fileTrailerFields = mutableListOf("Record Code", "File Total Amount", "File Total Groups", "File Total Records")
    val groupHeaderFields = mutableListOf("Record Code", "Bank Customer Number", "Receiver ABA", "Group Status", "Effective Date", "Effective Time", "Currency Code", "Filler")
    val groupTrailerFields = mutableListOf("Record Code", "Group Total Amount", "Group Total Accounts", "Group Total Records")
    val accountHeaderFields = mutableListOf("Record Code", "Bank Customer Account", "Currency Code", "Summary Type Code", "Summary Amount", "Summary Item Count", "Funds Type")
    val accountTrailerFields = mutableListOf("Record Code", "Account Control Total Amount", "Account Total Records")
    val transactionFields = mutableListOf("Record Code", "Detail Type Code", "Transaction Amount", "Funds Type", "Bank Ref Number", "Customer Ref Number", "Detail Text")

}