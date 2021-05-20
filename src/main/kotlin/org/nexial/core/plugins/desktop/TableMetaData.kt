package org.nexial.core.plugins.desktop

import org.nexial.commons.utils.TextUtils

data class TableMetaData(val headers: List<String?>?,
                         val rowCount: Int,
                         val cellTypes: Map<String, String>?,
                         val isNormalizeSpace: Boolean,
                         val columnCount: Int) {
    constructor() : this(null, 0, null, false, 0)

    override fun toString(): String {
        return """
               headers=${TextUtils.toString(headers, ",")}
               cellTypes=$cellTypes
               columnCount=$columnCount
               rowCount=$rowCount
               normalizeSpace=${isNormalizeSpace}
               """.trimIndent()
    }
}
