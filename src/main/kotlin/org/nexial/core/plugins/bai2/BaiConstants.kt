package org.nexial.core.plugins.bai2

import org.nexial.core.plugins.bai2.Validations.validateAlphanumeric
import org.nexial.core.plugins.bai2.Validations.validateAsciiPrintable
import org.nexial.core.plugins.bai2.Validations.validateNumeric

object BaiConstants {

    const val fieldDelim = ","
    const val recordDelim = "/"
    const val FILE_HEADER_CODE = "01"
    const val GROUP_HEADER_CODE = "02"
    const val ACCOUNT_IDENTIFIER_CODE = "03"
    const val TRANSACTION_CODE = "16"
    // todo: continuation_code type to be implemented
    const val CONTINUATION_CODE = "88"
    const val ACCOUNT_TRAILER_CODE = "49"
    const val GROUP_TRAILER_CODE = "98"
    const val FILE_TRAILER_CODE = "99"

    val fileHeaders = mutableListOf(
        Pair("Record Code", validateNumeric),
        Pair("Sender ABA", validateAlphanumeric),
        Pair("Receiver ABA", validateAlphanumeric),
        Pair("File Creation Date", validateNumeric),
        Pair("File Creation Time", validateNumeric),
        Pair("File ID", validateNumeric),
        Pair("Record Length", validateNumeric),
        Pair("File Block Size", validateNumeric),
        Pair("File Version Number", validateNumeric))

    val groupHeaders = mutableListOf(
        Pair("Record Code", validateNumeric),
        Pair("Bank Customer Number", validateAlphanumeric),
        Pair("Receiver ABA", validateAlphanumeric),
        Pair("Group Status", validateNumeric),
        Pair("Effective Date", validateNumeric),
        Pair("Effective Time", validateNumeric),
        Pair("Currency Code", validateNumeric),
        Pair("Date Modifier", validateNumeric))

    val accountHeaders = mutableListOf(
        Pair("Record Code", validateNumeric),
        Pair("Bank Customer Account", validateAsciiPrintable),
        Pair("Currency Code", validateAsciiPrintable),
        Pair("Summary Type Code", validateNumeric),
        Pair("Summary Amount", validateNumeric),
        Pair("Summary Item Count", validateNumeric),
        Pair("Funds Type", validateAsciiPrintable))

    val transactionFields = mutableListOf(
        Pair("Record Code", validateNumeric),
        Pair("Detail Type Code", validateNumeric),
        Pair("Transaction Amount", validateNumeric),
        Pair("Funds Type", validateAlphanumeric),
        Pair("Bank Ref Number", validateAlphanumeric),
        Pair("Customer Ref Number", validateAlphanumeric),
        Pair("Detail Text", validateAsciiPrintable))

    val accountTrailerFields = mutableListOf(
        Pair("Record Code", validateNumeric),
        Pair("Account Control Total Amount", validateNumeric),
        Pair("Account Total Records", validateNumeric))

    val groupTrailerFields = mutableListOf(
        Pair("Record Code", validateNumeric),
        Pair("Group Total Amount", validateNumeric),
        Pair("Group Total Accounts", validateNumeric),
        Pair("Group Total Records", validateNumeric))

    val fileTrailerFields = mutableListOf(
        Pair("Record Code", validateNumeric),
        Pair("File Total Amount", validateNumeric),
        Pair("File Total Groups", validateNumeric),
        Pair("File Total Records", validateNumeric))

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