package org.nexial.core.plugins.bai2

import org.nexial.core.plugins.bai2.BaiConstants.ACCOUNT_HEADER
import org.nexial.core.plugins.bai2.BaiConstants.ACCOUNT_TRAILER
import org.nexial.core.plugins.bai2.BaiConstants.FILE_HEADER
import org.nexial.core.plugins.bai2.BaiConstants.FILE_TRAILER
import org.nexial.core.plugins.bai2.BaiConstants.GROUP_HEADER
import org.nexial.core.plugins.bai2.BaiConstants.GROUP_TRAILER
import org.nexial.core.plugins.bai2.BaiConstants.TRANSACTION
import org.nexial.core.plugins.bai2.BaiConstants.accountHeaders
import org.nexial.core.plugins.bai2.BaiConstants.accountTrailerFields
import org.nexial.core.plugins.bai2.BaiConstants.fileHeaders
import org.nexial.core.plugins.bai2.BaiConstants.fileTrailerFields
import org.nexial.core.plugins.bai2.BaiConstants.groupHeaders
import org.nexial.core.plugins.bai2.BaiConstants.groupTrailerFields
import org.nexial.core.plugins.bai2.BaiConstants.transactionFields

object BaiRecordMeta {
    var fields: MutableList<Pair<String, (String) -> String>>? = null
    var type: String? = null

    fun instance(type: String): BaiRecordMeta {
        this.type = type
        when (type) {
            FILE_HEADER     -> fields = fileHeaders
            GROUP_HEADER    -> fields = groupHeaders
            ACCOUNT_HEADER  -> fields = accountHeaders
            TRANSACTION     -> fields = transactionFields
            ACCOUNT_TRAILER -> fields = accountTrailerFields
            GROUP_TRAILER   -> fields = groupTrailerFields
            FILE_TRAILER    -> fields = fileTrailerFields
        }
        return this
    }
}