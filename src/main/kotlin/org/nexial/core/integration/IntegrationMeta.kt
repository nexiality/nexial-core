package org.nexial.core.integration

open class IntegrationMeta {
    var processedTime = ""
    var labels = mutableSetOf<String>()
    var defects = mutableSetOf<String>()
    override fun toString(): String {
        return "processedTime='$processedTime', labels=$labels, defects=$defects"
    }
}

