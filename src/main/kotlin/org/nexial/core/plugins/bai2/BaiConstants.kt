package org.nexial.core.plugins.bai2

import org.nexial.core.plugins.bai2.Validations.validateAlphanumeric
import org.nexial.core.plugins.bai2.Validations.validateAsciiPrintable
import org.nexial.core.plugins.bai2.Validations.validateNumeric

object BaiConstants {

    const val fieldDelim = ","
    const val recordDelim = "/"
    const val GROUP = "Group"
    const val ACCOUNT = "Account"
    // todo: continuation_code type to be implemented
    // const val CONTINUATION_CODE = "88"

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
        Pair("Currency Code", validateAlphanumeric),
        Pair("Date Modifier", validateNumeric))

    val accountHeaders = mutableListOf(
        Pair("Record Code", validateNumeric),
        Pair("Bank Customer Account", validateAsciiPrintable),
        Pair("Currency Code", validateAlphanumeric),
        Pair("Summary Type Code", validateNumeric),
        Pair("Summary Amount", validateNumeric),
        Pair("Summary Item Count", validateNumeric),
        Pair("Funds Type", validateAlphanumeric))

    val transactionFields = mutableListOf(
        Pair("Record Code", validateNumeric),
        Pair("Detail Type Code", validateNumeric),
        Pair("Transaction Amount", validateNumeric),
        Pair("Funds Type", validateAlphanumeric),
        Pair("Bank Ref Number", validateAlphanumeric),
        Pair("Customer Ref Number", validateAlphanumeric),
        Pair("Detail Text", validateAsciiPrintable))

    val accountTrailers = mutableListOf(
        Pair("Record Code", validateNumeric),
        Pair("Account Control Total Amount", validateNumeric),
        Pair("Account Total Records", validateNumeric))

    val groupTrailers = mutableListOf(
        Pair("Record Code", validateNumeric),
        Pair("Group Total Amount", validateNumeric),
        Pair("Group Total Accounts", validateNumeric),
        Pair("Group Total Records", validateNumeric))

    val fileTrailers = mutableListOf(
        Pair("Record Code", validateNumeric),
        Pair("File Total Amount", validateNumeric),
        Pair("File Total Groups", validateNumeric),
        Pair("File Total Records", validateNumeric))

    class BaiRecordMeta(val code: String, val type: String, val fields: MutableList<Pair<String, (String) -> String>>)

    val fileHeaderMeta = BaiRecordMeta("01", "File Header", fileHeaders)
    val groupHeaderMeta = BaiRecordMeta("02", "Group Header", groupHeaders)
    val accountHeaderMeta = BaiRecordMeta("03", "Account Header", accountHeaders)
    val transactionMeta = BaiRecordMeta("16", "Transaction", transactionFields)
    val accountTrailerMeta = BaiRecordMeta("49", "Account Trailer", accountTrailers)
    val groupTrailerMeta = BaiRecordMeta("98", "Group Trailer", groupTrailers)
    val fileTrailerMeta = BaiRecordMeta("99", "File Trailer", fileTrailers)

}