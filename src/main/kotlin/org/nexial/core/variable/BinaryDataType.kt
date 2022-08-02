/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.variable

import org.nexial.core.ExecutionThread
import org.nexial.core.utils.OutputResolver

class BinaryDataType : ExpressionDataType<ByteArray> {
    constructor() : super()
    constructor(textValue: String?) {
        if (textValue == null || textValue.isEmpty()) {
            this.textValue = textValue
        } else {
            this.value = OutputResolver(data = textValue,
                                        context = ExecutionThread.get(),
                                        resolveAsFile = true,
                                        resolveUrl = true,
                                        replaceTokens = false,
                                        asBinary = true,
                                        compact = false).bytes
        }
        init()
    }

    constructor(value: ByteArray) {
        this.value = value
        init()
    }

    private var transformer = BinaryTransformer()

    override fun getName() = BINARY

    override fun getTransformer() = transformer

    override fun snapshot(): ExpressionDataType<ByteArray> {
        val snapshot = BinaryDataType()
        snapshot.transformer = transformer
        snapshot.value = value
        snapshot.init()
        return snapshot
    }

    override fun init() {
        if (value != null && value.isNotEmpty()) {
            textValue = "<binary>"
        } else if (textValue.isNotEmpty()) {
            value = textValue.toByteArray()
        }
    }

    companion object {
        // @JvmStatic
        const val BINARY = "BINARY"
    }
}
