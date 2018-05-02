package org.nexial.core.plugins.bai2

object BaiConstants {

    const val fieldDelim = ","
    const val recordDelim = "/"
    const val FILE_HEADER_CODE = "01"
    const val GROUP_HEADER_CODE = "02"
    const val ACCOUNT_IDENTIFIER_CODE = "03"
    const val TRANSACTION_CODE = "16"
    // todo: not yet impl.
    const val CONTINUATION_CODE = "88"
    const val ACCOUNT_TRAILER_CODE = "49"
    const val GROUP_TRAILER_CODE = "98"
    const val FILE_TRAILER_CODE = "99"

    val fileHeaders = mutableListOf("Record Code", "Sender ABA", "Receiver ABA", "File Creation Date", "File Creation Time", "File ID", "Record Length", "File Block Size", "File Version Number")
    val groupHeaders = mutableListOf("Record Code", "Bank Customer Number", "Receiver ABA", "Group Status", "Effective Date", "Effective Time", "Currency Code", "Date Modifier")
    val accountHeaders = mutableListOf("Record Code", "Bank Customer Account", "Currency Code", "Summary Type Code", "Summary Amount", "Summary Item Count", "Funds Type")
    val transactionFields = mutableListOf("Record Code", "Detail Type Code", "Transaction Amount", "Funds Type", "Bank Ref Number", "Customer Ref Number", "Detail Text")
    val accountTrailerFields = mutableListOf("Record Code", "Account Control Total Amount", "Account Total Records")
    val groupTrailerFields = mutableListOf("Record Code", "Group Total Amount", "Group Total Accounts", "Group Total Records")
    val fileTrailerFields = mutableListOf("Record Code", "File Total Amount", "File Total Groups", "File Total Records")

    const val FILE_HEADER = "File Header"
    const val GROUP = "Group"
    const val GROUP_HEADER = "Group Header"
    const val ACCOUNT = "Account"
    const val ACCOUNT_HEADER = "Account Header"
    const val TRANSACTION = "Transaction"
    const val ACCOUNT_TRAILER = "Account Trailer"
    const val GROUP_TRAILER = "Group Trailer"
    const val FILE_TRAILER = "File Trailer"


}